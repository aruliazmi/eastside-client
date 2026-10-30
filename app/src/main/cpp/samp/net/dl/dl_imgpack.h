#pragma once

// IMG v2 archive packer for 0.3.DL custom models.
// Packs downloaded DFF/TXD files into a GTA SA "VER2" IMG archive so the
// game's streaming system (CStreaming::AddImageToList) can load them.
//
// IMG v2 layout (verified against real game IMG):
//   offset 0: "VER2" (4 bytes)
//   offset 4: entry count (uint32 LE)
//   each entry (32 bytes): offset_sectors (u32 LE), size_sectors (u32 LE),
//                          name[24] (null-padded)
//   file data follows, 2048-byte sector aligned, first entry at sector 2.

#include <cstdint>
#include <string>
#include <vector>
#include <unordered_map>

namespace DLImgPack {

// One file to pack: source path on disk + name inside the archive.
struct ImgEntry {
    std::string srcPath;   // absolute path to the file to pack
    std::string arcName;   // name inside IMG (max 23 chars + NUL)
};

struct SectorSpan {
    uint32_t offset;
    uint32_t count;
};

using LayoutMap = std::unordered_map<std::string, SectorSpan>;

static const size_t IMG_SECTOR_SIZE = 2048;
static const size_t IMG_MAX_NAME    = 24;   // incl. NUL
static const size_t IMG_MAX_ENTRIES = 4096; // sanity cap
// VER2 stores a sector offset and count as uint32, so the real format limit is
// 2^32 * 2048 bytes (~8 TiB). The old 512 MB figure was an arbitrary cap and it
// rejected a real server: State Side ships 79 DFF + 91 TXD totalling 537 MB
// (the largest single TXD is 17 MB), so PackImgV2 failed with "archive exceeds
// size cap", registerCustomModels reported registered=0, and every custom model
// fell back to a base model even though all 169 files had downloaded fine.
//
// 2 GiB keeps the whole archive inside a signed 32-bit byte offset too, which
// matters because parts of the streaming code store sizes in int32.
static const uint64_t IMG_MAX_TOTAL = 2048ULL * 1024ULL * 1024ULL; // 2 GiB cap

// Packs files into a VER2 IMG at outPath.
// Returns true on success, false on any error (outError filled).
// Security: validates name length, file sizes (no overflow), total size cap,
// and rejects path traversal in arc names.
bool PackImgV2(const std::vector<ImgEntry>& entries,
               const std::string& outPath,
               std::string& outError,
               LayoutMap* outLayout = nullptr);

} // namespace DLImgPack
