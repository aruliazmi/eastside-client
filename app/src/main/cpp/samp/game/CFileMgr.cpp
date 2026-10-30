//
// Created by x1y2z on 14.04.2023.
//

#include "CFileMgr.h"
#include "main.h"
#include "../vendor/shadowhook/patch.h"
#include <android/asset_manager.h>
#include <stdio.h>
#include <strings.h>
#include <ctype.h>
#include <string>
#include <algorithm>
#include <dirent.h>
#include <sys/stat.h>

extern AAssetManager* g_pAssetManager;

/* Files the engine probes for but that never ship on Android. Missing them is
 * normal and must not be reported as an error; listing them in one place keeps
 * CFileMgr and the NVFOpen hook from drifting apart (they used to duplicate the
 * allowlist and both forgot KeyboardMappings.cfg). */
bool IsOptionalMissingFile(const char* path) {
    if (!path) return true;
    static const char* kOptional[] = {
        ".idx", "gta_sa.set", "gtasatelem.set", "gta3.ini",
        "CINFO.BIN", "MINFO.BIN", "GTASAsf", "Adjustable.cfg",
        "KeyboardMappings.cfg",
    };
    for (const char* needle : kOptional) {
        if (strstr(path, needle)) return true;
    }
    return false;
}

static int asset_read(void* cookie, char* buf, int size) {
    return AAsset_read((AAsset*)cookie, buf, size);
}
static int asset_write(void* cookie, const char* buf, int size) {
    return -1; // Read only
}
static fpos_t asset_seek(void* cookie, fpos_t offset, int whence) {
    return AAsset_seek((AAsset*)cookie, offset, whence);
}
static int asset_close(void* cookie) {
    AAsset_close((AAsset*)cookie);
    return 0;
}

static AAsset* OpenAssetCompat(const char* path) {
    // 1. Try exact match
    AAsset* asset = AAssetManager_open(g_pAssetManager, path, AASSET_MODE_UNKNOWN);
    if (asset) return asset;

    // 2. Try lowercase
    std::string lowerPath = path;
    std::transform(lowerPath.begin(), lowerPath.end(), lowerPath.begin(), ::tolower);
    asset = AAssetManager_open(g_pAssetManager, lowerPath.c_str(), AASSET_MODE_UNKNOWN);
    if (asset) return asset;
    
    // 3. Try "Text" prefix normalization (common case "TEXT/..." -> "Text/...")
    if (strncasecmp(path, "TEXT/", 5) == 0) {
        std::string textPath = "Text/" + std::string(path + 5);
        asset = AAssetManager_open(g_pAssetManager, textPath.c_str(), AASSET_MODE_UNKNOWN);
        if (asset) return asset;
        
        std::string lowerFilename = path + 5;
        std::transform(lowerFilename.begin(), lowerFilename.end(), lowerFilename.begin(), ::tolower);
        textPath = "Text/" + lowerFilename;
        asset = AAssetManager_open(g_pAssetManager, textPath.c_str(), AASSET_MODE_UNKNOWN);
        if (asset) return asset;
    }

    return nullptr;
}

static bool ResolveCaseInsensitive(const char* base, const char* rel, char* out, size_t outSize) {
    if (!base || !rel || !out || outSize == 0) return false;

    std::string current = base;
    if (!current.empty() && current.back() != '/') current += '/';

    std::string relStr = rel;
    for (char& c : relStr) {
        if (c == '\\') c = '/';
    }

    size_t start = relStr.find_first_not_of('/');
    if (start == std::string::npos) return false;

    size_t pos = start;
    while (pos < relStr.size()) {
        size_t next = relStr.find('/', pos);
        std::string segment = (next == std::string::npos) ? relStr.substr(pos) : relStr.substr(pos, next - pos);
        if (segment.empty() || segment == ".") {
            if (next == std::string::npos) break;
            pos = next + 1;
            continue;
        }

        std::string candidate = current + segment;
        struct stat st;
        if (stat(candidate.c_str(), &st) == 0) {
            current = candidate;
        } else {
            DIR* dir = opendir(current.c_str());
            if (!dir) return false;
            struct dirent* entry;
            bool found = false;
            while ((entry = readdir(dir)) != nullptr) {
                if (strcasecmp(entry->d_name, segment.c_str()) == 0) {
                    current += entry->d_name;
                    found = true;
                    break;
                }
            }
            closedir(dir);
            if (!found) return false;
        }

        if (next == std::string::npos) break;
        current += '/';
        pos = next + 1;
    }

    if (current.size() >= outSize) return false;
    strncpy(out, current.c_str(), outSize - 1);
    out[outSize - 1] = '\0';
    return true;
}

void CFileMgr::SetDir(const char *path) {
    ( ( void(*)(const char *path) )(g_libGTASA + (VER_x32 ? 0x003F0C54 + 1 : 0x4D293C)) )(path);
}

FILE* CFileMgr::OpenFile(const char *path, const char *mode) {
    if (!path) return nullptr;

    // Check for Text or fonts in assets (Case insensitive)
    if (g_pAssetManager && (strcasestr(path, "Text/") || strcasestr(path, "fonts/") || strcasestr(path, "scache") || strcasestr(path, "stream.ini") || strcasestr(path, "version.txt"))) {
        AAsset* asset = OpenAssetCompat(path);
        if (asset) {
            FILE* assetFile = funopen(asset, asset_read, asset_write, asset_seek, asset_close);
            if (assetFile) {
                return assetFile;
            }
            AAsset_close(asset);
        }
    }

    char cleanRel[512];
    strncpy(cleanRel, path, sizeof(cleanRel) - 1);
    cleanRel[sizeof(cleanRel) - 1] = '\0';
    for (char* p = cleanRel; *p; ++p) {
        if (*p == '\\') *p = '/';
    }

    snprintf(ms_path, sizeof(ms_path), "%s%s", g_pszStorage, cleanRel);
    auto file = fopen(ms_path, mode);

    if (!file) {
        char resolved[512];
        if (ResolveCaseInsensitive(g_pszStorage, cleanRel, resolved, sizeof(resolved))) {
            file = fopen(resolved, mode);
            if (file) {
                FLog("OpenFile case fallback: %s -> %s", cleanRel, resolved);
                strncpy(ms_path, resolved, sizeof(ms_path) - 1);
                ms_path[sizeof(ms_path) - 1] = '\0';
            }
        }
    }

    if (!file) {
        if (!IsOptionalMissingFile(ms_path)) {
            FLog("Fail open file %s", ms_path);
        }
    }
    return file;
}

int32_t CFileMgr::CloseFile(FILE* file) {
    return fclose(file);
}

void CFileMgr::Initialise() {
    CHook::CallFunction<void>("_ZN8CFileMgr10InitialiseEv");
}
