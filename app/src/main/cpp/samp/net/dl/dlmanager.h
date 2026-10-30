#pragma once

#include <cstdint>
#include <string>
#include <jni.h>
#include <vector>
#include <deque>
#include <unordered_map>
#include <unordered_set>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <atomic>
#include <chrono>
#include "dl_imgpack.h"
#include "../../vendor/raknet/PacketPriority.h"
#include "../../vendor/raknet/BitStream.h"
#include "../../vendor/raknet/RakClientInterface.h"
#include "../../vendor/raknet/NetworkTypes.h"

// Model types in SA-MP 0.3.DL wire format
enum class DLModelType : uint8_t {
    Skin = 1,
    Object = 2
};

// Number of parallel HTTP workers, matching SA-MP PC's artwork downloader pool.
#define DL_MAX_CONCURRENT 5

// ---- Protocol / limit constants (named so the wire format lives in one place) --
static const size_t DL_RPC179_WIRE_BYTES   = 39u;   // fixed ModelRequest payload
static const size_t DL_RPC183_MIN_BYTES    = 7u;    // arg+type+crc+urlLen(1)+>=0
static const size_t DL_MAX_URL_BYTES       = 511u;  // dynstr8 is <=255 but be safe
static const size_t DL_MAX_HTTP_HEADER     = 65536u; // cap on response header buffer
static const size_t DL_MAX_REDIRECTS       = 3;
static const uint32_t DL_HTTP_STALL_MS     = 15000; // no bytes for this long => fail
static const uint32_t DL_CONNECT_TIMEOUT_MS= 8000;
// After the download settles, wait this long before mutating engine tables and
// telling the server we are ready. See DLManager::ProcessTick.
static const uint32_t DL_REGISTER_DELAY_MS = 900;
// After RegisterCustomModels() finishes, wait this long before sending RPC 184.
// The server answers with a spawn RPC, which must not land while engine tables
// are still being touched.
static const uint32_t DL_READY_DELAY_MS    = 400;
static const uint32_t DL_URL_WAIT_MS       = 10000; // wait for RPC 183 after request
static const int      DL_MAX_ATTEMPTS      = 4;
static const int16_t  DL_VIRT_MIN          = 18631; // safe internal CModelInfo range
static const int16_t  DL_VIRT_MAX          = 19999;

struct DLModelInfo {
    uint32_t poolID;
    int32_t  count;
    uint8_t  type;          // 1 = Skin, 2 = Object
    int32_t  virtualWorld;
    int32_t  baseId;
    int32_t  newId;
    uint32_t dffChecksum;
    uint32_t txdChecksum;
    uint32_t dffSize;
    uint32_t txdSize;
    uint8_t  timeOn;
    uint8_t  timeOff;
    bool     dffCached;
    bool     txdCached;
    int16_t  virtualModelId; // Safe internal range: 18631..19999
};

// One HTTP asset waiting to be fetched. Created when RPC 179 registers a model
// whose file is missing from cache.
struct DLFileTask {
    uint32_t crc{0};
    uint32_t size{0};
    bool     isDff{true};
    std::string url;         // filled by RPC 183 (ModelUrl)
    std::string hexName;     // 0x<CRC>.dff for logging / UI
    int      attempts{0};
    bool     started{false};  // RPC 181/182 sent for this file
    bool     done{false};
    bool     failed{false};
    std::string lastError;    // last failure reason, surfaced to the UI

    // Skip-fetch fast path: if attempts>0 and url was already obtained we keep it.
    bool     haveUrl() const { return !url.empty(); }
};

class DLManager {
public:
    static DLManager& Instance();

    void Initialize(const std::string& serverIp, int serverPort, const std::string& storagePath, RakClientInterface* pRakClient);
    void Reset();
    void ProcessTick();
    void RenderImGui();

    // RPC Handlers (invoked on the RakNet UpdateNetworkLoop thread - never block)
    void OnModelRequest(RPCParameters* rpcParams);
    void OnModelFile(RPCParameters* rpcParams);
    void OnDownloadCompleted(RPCParameters* rpcParams);

    // Cache & Integrity Helpers
    std::string GetCachePath() const { return m_cachePath; }
    std::string GetModelPath(uint32_t crc, bool isDff) const;
    bool IsModelCached(uint32_t crc, uint32_t expectedSize, bool isDff) const;
    /** Rejects truncated or non-RenderWare model files before the engine parses them. */
    bool IsRenderWareModelValid(const std::string& path, bool isDff) const;
    static uint32_t CalculateCRC32(const void* data, size_t length);
    static uint32_t CalculateFileCRC32(const std::string& filepath);

    // Virtual Model Mapping (Part 4 solution)
    int16_t GetVirtualModelId(int32_t networkId);
    int32_t GetNetworkModelId(int16_t virtualId);

    // Custom model registration (0.3.DL PR #3): packs downloaded DFF/TXD
    // into a custom IMG, registers it with the streaming system, and
    // allocates CModelInfo slots. Called once on the game thread after
    // all downloads settle. Returns true if at least one model registered.
    bool RegisterCustomModels();
    // True if virtualId was successfully registered via RegisterCustomModels.
    // Used by the fallback guard so unregistered models fall back to base.
    bool IsModelRegistered(int16_t virtualId) const;

    bool IsInitialized() const { return m_bInitialized.load(); }
    bool IsDownloadFinished() const { return m_bDownloadFinished.load(); }
    int GetTotalModels() const { return m_totalModels.load(); }
    int GetDownloadedModels() const { return m_downloadedModels.load(); }
    int GetActiveWorkers() const { return m_activeWorkers.load(); }
    float GetCurrentSpeedKBps() const { return m_speedKBps.load(); }
    void GetProgressStats(size_t& queued, size_t& active, size_t& done, size_t& failed) const;

    void SendFinishDownload();
    void ToggleOverlay();
    void RequestRetryFailed();  // user action: re-arm all failed tasks for one more pass
    bool IsOverlayForced() const { return m_bForceShowOverlay; }
    void PushChat(const std::string& line) const;  // thread-safe: queues for game thread

    /** Diagnostics: snapshot of download/registration state for crash logs. */
    size_t DebugRegisteredCount() const;
    int    DebugTaskCount() const;
    int    DebugModelCount() const;
    int    DebugTotalModels() const;
    bool   DebugSawAnyModel() const;

    /**
     * True while dialogs from the server must be held back: custom models are
     * being downloaded, or the registration pass has not finished yet. The
     * server never waits for OnPlayerFinishedDownloading, so its login dialog
     * would otherwise appear on top of the download overlay and racing the
     * CTxdStore/CModelInfo rewrite.
     */
    bool IsBlockingDialogs() const;

    /** Queue a dialog that arrived during the download; only the newest is kept. */
    void QueueDialog(uint8_t style, uint16_t id,
                     const std::string& title, const std::string& message,
                     const std::string& button1, const std::string& button2);

    /** Flush the queued dialog, if any, to the Java dialog surface. */
    void FlushQueuedDialog();
    int Next183DumpIndex();

private:
    // ---- Dialog hold-back (see IsBlockingDialogs) ------------------------
    // Only the newest dialog is kept: the login flow re-sends the same dialog ID
    // as the player's state changes, and replaying stale ones would confuse it.
    mutable std::mutex m_dialogMutex;
    bool        m_hasQueuedDialog{false};
    uint8_t     m_qDialogStyle{0};
    uint16_t    m_qDialogId{0};
    std::string m_qDialogTitle;
    std::string m_qDialogMessage;
    std::string m_qDialogButton1;
    std::string m_qDialogButton2;
    DLManager();
    ~DLManager();

    // --- RPC handshake side (runs on the RakNet update thread, never blocks) ---
    void RequestFileFromServer(uint32_t crc, bool isDff);
    void ProcessRpcOutbox();
    void RecomputeProgress();
    void FlushChatQueue();          // game thread drains worker log lines here

    // --- HTTP worker side (runs on DL_MAX_CONCURRENT detached threads) ---
    void WorkerLoop();
    // Returns true and fills outBytes on success. On failure sets outError.
    bool FetchToFile(const DLFileTask& task, uint64_t& outBytes, std::string& outError);

    // cache validation without deleting a file another worker owns
    bool ValidateOrPurge(const std::string& path, uint32_t crc, uint32_t expectedSize);

    std::string m_serverIp;
    int m_serverPort;
    std::string m_cachePath;
    mutable std::mutex m_cachePathMutex;   // guards m_cachePath string read by workers
    RakClientInterface* m_pRakClient;      // game-thread owned; nulled on Reset

    mutable std::mutex m_mutex;

    // Model registry
    std::vector<DLModelInfo> m_models;
    std::unordered_map<int32_t, int16_t> m_networkToVirtual;
    std::unordered_map<int16_t, int32_t> m_virtualToNetwork;
    int16_t m_nextVirtualId{DL_VIRT_MIN};
    // Diagnostic log throttle: number of files reported ok this session.
    int m_logOkCount{0};

    // CRCs whose file has already been CRC-checked and structurally validated
    // this session. Without this, IsModelCached re-read every file (543 MB on a
    // reported server) on the game thread for every manifest packet.
    mutable std::mutex        m_verifyMutex;
    mutable std::unordered_set<uint32_t> m_verified;

    // Background IMG pack state. The pack writes 500+ MB and must not run on the
    // game thread; RegisterCustomModels() starts it and polls until Done.
    enum class ImgPackState { None, Running, Done, Failed };
    ImgPackState             m_imgPackState{ImgPackState::None};
    std::string              m_imgPackError;
    DLImgPack::LayoutMap     m_packedLayout;
    void StartImgPackAsync();          // called with m_verifyMutex held
    /** Read custom TXDs out of the packed IMG into their CTxdStore slots. */
    void InstallCustomTxds(const DLImgPack::LayoutMap& layout, int imgIndex,
                           const std::string& imgFullPath);
    bool    m_bVirtualIdExhausted{false};

    // Custom models successfully registered with the game engine
    // (via RegisterCustomModels). Guarded by m_mutex.
    std::unordered_set<int16_t> m_registeredModels;
    bool m_bModelsRegistered{false};

    // Download queue. Keyed by (crc,isDff) so DFF/TXD CRCs never alias.
    std::deque<DLFileTask> m_tasks;
    std::unordered_set<uint64_t> m_knownKey;         // (crc<<1)|isDff
    std::unordered_map<uint32_t, size_t> m_taskIndex; // crc -> active task idx hint

    // RPC outbox: workers/callbacks must not touch RakClient, only push here.
    // ProcessTick() drains it on the game thread.
    std::deque<std::pair<uint32_t, bool>> m_rpcOutbox; // (crc, isDff)
    std::atomic<bool> m_bFinishRequested{false};
    // Set once we have actually transmitted RPC 184; used to re-arm if a late 179 arrives.
    bool m_bFinishSent{false};
    // Tick at which the download settled. Registration and RPC 184 wait until
    // DL_REGISTER_DELAY_MS has elapsed so the engine state has stopped moving
    // before the server is told we are ready (see ProcessTick).
    uint32_t m_finishQueueTick{0};
    // Tick at which RegisterCustomModels() last completed; gates RPC 184.
    uint32_t m_registerDoneTick{0};
    bool m_bSawAnyModel{false};      // any RPC 179 ever received this session

    // Worker pool
    std::mutex m_workMutex;
    std::condition_variable m_workCv;
    std::vector<std::thread> m_workers;
    std::atomic<bool> m_bShutdown{false};
    std::atomic<int> m_activeWorkers{0};

    // chat marshal (worker/raknet -> game thread UI)
    mutable std::mutex m_chatMutex;
    mutable std::deque<std::string> m_chatQueue;

    std::atomic<bool> m_bInitialized{false};
    std::atomic<bool> m_bDownloadFinished{false};
    std::atomic<int>  m_totalModels{0};
    std::atomic<int>  m_downloadedModels{0};
    std::atomic<int>  m_failedModels{0};
    std::atomic<float> m_speedKBps{0.0f};

    uint32_t m_lastSpeedSampleTick{0};
    uint64_t m_lastSpeedSampleBytes{0};
    std::atomic<uint64_t> m_totalBytesRx{0};

    uint32_t m_connectTime{0};
    uint32_t m_lastModelRequestTick{0};
    uint32_t m_finishTime{0};
    bool m_bForceShowOverlay{false};
    bool m_bReportedNoManifest{false};
    std::atomic<int> m_rpc183DumpCount{0};
    std::string m_lastActivity;
};

// Called once from JNI_OnLoad (which runs with the app class loader context)
// to cache the DLHttpClient class/method refs as global refs.
// Worker threads must NEVER call FindClass for app classes: a thread attached
// via AttachCurrentThread has no app class loader context, so FindClass
// returns NULL with a pending exception — and DetachCurrentThread with a
// pending exception aborts the VM (this was the 0.3.DL download crash).
void DLManager_InitJniCache(JNIEnv* env);
