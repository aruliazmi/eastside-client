//
// Created by x1y2z on 11.01.2023.
//

#include "TextureDatabaseRuntime.h"
#include "../../main.h"
#include "../vendor/shadowhook/patch.h"
#include "../../net/dl/custom_txd_install.h"

// The engine loads its own texture-database list inside Initialise3D, without
// any disk probe. When the requested variant's payload is not on disk (e.g.
// cutscene ships .txt only, or ANGLE asks for .dxt of an etc-only package),
// Load() still returns a half-initialised db; the engine registers it and the
// first GetTexture streams from a NULL file handle -> SIGSEGV 0x110 in
// _rwStreamReadChunkHeader (tombstone_02/03/05/08).
//
// Gate the engine's Load(): verify all three files exist for the requested
// format before letting it run; otherwise return NULL so the engine skips the
// database cleanly (missing textures instead of a crash).
static bool TdbFilesExist(const char* db, TextureDatabaseFormat f)
{
    static const char* kTag[] = { "unc", "dxt", "360", "ps3", "pvr", "etc", "default", "all" };
    const char* tag = (f >= 0 && f <= 7) ? kTag[f] : "dxt";

    char path[512];
    const char* base = g_pszStorage ? g_pszStorage : "";
    for (int i = 0; i < 3; i++) {
        snprintf(path, sizeof(path), "%stexdb/%s/%s.%s.%s",
                 base, db, db, tag, i == 0 ? "dat" : (i == 1 ? "tmb" : "toc"));
        FILE* fp = fopen(path, "rb");
        if (!fp) return false;
        fclose(fp);
    }
    return true;
}

static TextureDatabaseRuntime* (*orig_TdbLoad)(const char*, bool, TextureDatabaseFormat) = nullptr;

static TextureDatabaseRuntime* TdbLoad_hook(const char* withName, bool fullyLoad,
                                            TextureDatabaseFormat forcedFormat)
{
    if (!withName || !g_pszStorage) return orig_TdbLoad(withName, fullyLoad, forcedFormat);

    // DF_Default lets the engine pick from GL extensions; resolve it the way
    // the engine does by probing every compressed variant in priority order.
    TextureDatabaseFormat order[] = { forcedFormat, TextureDatabaseFormat::DF_DXT,
                                      TextureDatabaseFormat::DF_ETC,
                                      TextureDatabaseFormat::DF_PVR };
    for (TextureDatabaseFormat f : order) {
        if (TdbFilesExist(withName, f)) {
            FLog("engine texdb '%s' -> .%s (gated)", withName,
                 f == TextureDatabaseFormat::DF_ETC ? "etc"
                 : f == TextureDatabaseFormat::DF_PVR ? "pvr" : "dxt");
            return orig_TdbLoad(withName, fullyLoad, f);
        }
    }
    FLog("engine texdb '%s' has no payload in any variant - skipping", withName);
    return nullptr;
}

TextureDatabaseRuntime* TextureDatabaseRuntime::Load(const char *withName, bool fullyLoad, TextureDatabaseFormat forcedFormat)
{
    return CHook::CallFunction<TextureDatabaseRuntime*>(g_libGTASA + (VER_x32 ? 0x001EA864 + 1 : 0x28771C), withName, fullyLoad, forcedFormat);
}

static TDBArray<TextureDatabaseRuntime*>* GetRegisteredArray() {
    if (!g_libGTASA) return nullptr;
    return (TDBArray<TextureDatabaseRuntime*>*)(g_libGTASA + (VER_x32 ? 0x6BD174 : 0x89AA08));
}

bool TextureDatabaseRuntime::IsDbRegistered(TextureDatabaseRuntime* db) {
    if (!db) return false;
    auto* reg = GetRegisteredArray();
    if (!reg || !reg->dataPtr) return false;
    for (unsigned int i = 0; i < reg->numEntries; i++) {
        if (reg->dataPtr[i] == db) return true;
    }
    return false;
}

void TextureDatabaseRuntime::CleanRegisteredDatabases() {
    auto* reg = GetRegisteredArray();
    if (!reg || !reg->dataPtr) return;
    unsigned int validCount = 0;
    for (unsigned int i = 0; i < reg->numEntries; i++) {
        TextureDatabaseRuntime* db = reg->dataPtr[i];
        if (db != nullptr && db->name != nullptr && db->hashOffsets.dataPtr != nullptr) {
            reg->dataPtr[validCount++] = db;
        }
    }
    reg->numEntries = validCount;
}

static RwTexture* (*orig_GetTexture)(const char*) = nullptr;

static RwTexture* GetTexture_hook(const char* name) {
    if (!name || !name[0]) return nullptr;
    TextureDatabaseRuntime::CleanRegisteredDatabases();

    // Check custom 0.3.DL downloaded textures first
    RwTexture* custom = CustomTxdInstall::FindTexture(name);
    if (custom) {
        return custom;
    }

    return orig_GetTexture(name);
}

static RwTexture* (*orig_TxdStoreFindCB)(const char*) = nullptr;

static RwTexture* TxdStoreFindCB_hook(const char* name) {
    if (!name || !name[0]) return nullptr;

    RwTexture* custom = CustomTxdInstall::FindTexture(name);
    if (custom) {
        return custom;
    }

    if (orig_TxdStoreFindCB) {
        return orig_TxdStoreFindCB(name);
    }
    return nullptr;
}

void TextureDatabaseRuntime::InstallLoadGate()
{
    CHook::InlineHook("_ZN22TextureDatabaseRuntime4LoadEPKcb21TextureDatabaseFormat",
                      &TdbLoad_hook, &orig_TdbLoad);
    CHook::InlineHook("_ZN22TextureDatabaseRuntime10GetTextureEPKc",
                      &GetTexture_hook, &orig_GetTexture);
    CHook::InlineHook("_ZN9CTxdStore14TxdStoreFindCBEPKc",
                      &TxdStoreFindCB_hook, &orig_TxdStoreFindCB);
}

void TextureDatabaseRuntime::Register(TextureDatabaseRuntime *toRegister) {
    if (!toRegister || !toRegister->name || !toRegister->hashOffsets.dataPtr) {
        return;
    }
    if (IsDbRegistered(toRegister)) {
        return;
    }
    CHook::CallFunction<void>(g_libGTASA + (VER_x32 ? 0x1E9B48 + 1 : 0x2865D8), toRegister);
}

void TextureDatabaseRuntime::Unregister(TextureDatabaseRuntime *toUnregister) {
    if (!toUnregister) return;
    CHook::CallFunction<void>(g_libGTASA + (VER_x32 ? 0x1E9C00 + 1 : 0x2866A4), toUnregister);
}

RwTexture* TextureDatabaseRuntime::GetTexture(const char *name) {
    if (!name || !name[0]) return nullptr;
    TextureDatabaseRuntime::CleanRegisteredDatabases();

    RwTexture* custom = CustomTxdInstall::FindTexture(name);
    if (custom) {
        return custom;
    }

    if (orig_GetTexture) {
        return orig_GetTexture(name);
    }
    return ((RwTexture*(*)(const char*))(g_libGTASA + (VER_x32 ? 0x001E9C64 + 1 : 0x286718)))(name);
}

void TextureDatabaseRuntime::UpdateStreaming(float deltaTime, bool flush) {
    CHook::CallFunction<void>(g_libGTASA + (VER_x32 ? 0x1E9518 + 1 : 0x285DBC), deltaTime, flush);
}

TextureDatabaseRuntime* TextureDatabaseRuntime::GetDatabase(const char *dbName) {
    return CHook::CallFunction<TextureDatabaseRuntime*>(g_libGTASA + (VER_x32 ? 0x1EAC0C + 1 : 0x287AF4), dbName);
}

void TextureDatabaseRuntime::InjectHooks() {
}

RwBool TextureAnnihilate(RwTexture *texture) {
    return CHook::CallFunction<RwBool>(g_libGTASA + (VER_x32 ? 0x1DB33C + 1 : 0x273A1C), texture);
}
