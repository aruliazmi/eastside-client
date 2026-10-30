#include "custom_txd_install.h"

#include "../../game/RW/RenderWare.h"
#include "../../game/Models/BaseModelInfo.h"
#include "../../main.h"
#include "../../vendor/shadowhook/patch.h"

#include <cstring>
#include <mutex>
#include <unordered_map>
#include <string>
#include <algorithm>

// RpMaterialSetTexture(dst, src) calls the real C++ function by address.
// Mangled symbol: _Z20RpMaterialSetTextureP10RpMaterialP9RwTexture
// (arm64 0x2BE620, armv7 0x217224).
static void* RpMaterialSetTexture(void* material, void* texture) {
    return CHook::CallFunction<void*>(
        g_libGTASA + (VER_x32 ? 0x00217224 + 1 : 0x2BE620), material, texture);
}

namespace CustomTxdInstall {
namespace {

std::mutex g_texMutex;
std::unordered_map<std::string, RwTexture*> g_textures;

std::string NormalizeName(const std::string& str) {
    std::string s = str;
    for (char& c : s) {
        if (c >= 'A' && c <= 'Z') c += ('a' - 'A');
    }
    size_t dot = s.rfind('.');
    if (dot != std::string::npos && dot > 0) {
        std::string ext = s.substr(dot);
        if (ext == ".png" || ext == ".tga" || ext == ".bmp" || ext == ".dds") {
            s = s.substr(0, dot);
        }
    }
    return s;
}

struct PatchCtx {
    RwTexture* texture;
    int patched;
};

RpMaterial* PatchMaterial(RpMaterial* material, void* data) {
    auto* ctx = static_cast<PatchCtx*>(data);
    if (!material || !ctx->texture) return material;
    RpMaterialSetTexture(material, ctx->texture);
    ++ctx->patched;
    return material;
}

void PatchAtomic(RpAtomic* atomic, PatchCtx* ctx) {
    if (!atomic) return;
    RpGeometry* geometry = RpAtomicGetGeometry(atomic);
    if (!geometry) return;
    RpGeometryForAllMaterials(geometry, PatchMaterial, ctx);
}

void PatchObject(RwObject* object, PatchCtx* ctx, int depth) {
    if (!object || depth > 8) return;

    if (object->type == 1) {
        PatchAtomic(reinterpret_cast<RpAtomic*>(object), ctx);
        return;
    }

    auto* frame = reinterpret_cast<RwFrame*>(object);
    for (RwFrame* child = frame->child; child; child = child->next) {
        PatchObject(reinterpret_cast<RwObject*>(child), ctx, depth + 1);
    }
}

// Builds an RwTexture from RGBA8 pixels.
RwTexture* MakeTexture(const CustomTxd::Texture& tex) {
    RwRaster* raster = RwRasterCreate(tex.width, tex.height, 32,
                                      rwRASTERFORMAT8888 | rwRASTERTYPETEXTURE);
    if (!raster) return nullptr;

    RwUInt8* pixels = RwRasterLock(raster, 0, rwRASTERLOCKWRITE);
    if (!pixels) {
        return nullptr;
    }
    memcpy(pixels, tex.rgba.data(),
           static_cast<size_t>(tex.width) * tex.height * 4);
    RwRasterUnlock(raster);

    RwTexture* texture = RwTextureCreate(raster);
    if (!texture) return nullptr;
    RwTextureSetName(texture, tex.name.c_str());
    RwTextureSetFilterModeMacro(texture, rwFILTERLINEAR);
    RwTextureSetAddressingMacro(texture, rwTEXTUREADDRESSWRAP);
    return texture;
}

} // namespace

int RegisterTextures(const std::vector<CustomTxd::Texture>& textures) {
    if (textures.empty()) return 0;
    std::lock_guard<std::mutex> lock(g_texMutex);
    int created = 0;
    for (const auto& tex : textures) {
        if (tex.name.empty() || tex.rgba.empty()) continue;
        std::string norm = NormalizeName(tex.name);
        if (g_textures.find(norm) != g_textures.end()) continue;

        RwTexture* createdTex = MakeTexture(tex);
        if (!createdTex) continue;

        g_textures[norm] = createdTex;
        std::string rawLower = tex.name;
        for (char& c : rawLower) {
            if (c >= 'A' && c <= 'Z') c += ('a' - 'A');
        }
        if (rawLower != norm) {
            g_textures[rawLower] = createdTex;
        }
        ++created;
    }
    return created;
}

RwTexture* FindTexture(const char* name) {
    if (!name || !name[0]) return nullptr;
    std::string norm = NormalizeName(name);

    std::lock_guard<std::mutex> lock(g_texMutex);
    auto it = g_textures.find(norm);
    if (it != g_textures.end()) {
        return it->second;
    }

    std::string rawLower = name;
    for (char& c : rawLower) {
        if (c >= 'A' && c <= 'Z') c += ('a' - 'A');
    }
    it = g_textures.find(rawLower);
    if (it != g_textures.end()) {
        return it->second;
    }

    return nullptr;
}

Stats ApplyToModel(void* rootObject, const std::vector<CustomTxd::Texture>& textures) {
    Stats stats;
    if (!rootObject || textures.empty()) return stats;

    std::lock_guard<std::mutex> lock(g_texMutex);

    RwTexture* primary = nullptr;
    for (const auto& tex : textures) {
        if (tex.name.empty() || tex.rgba.empty()) continue;
        std::string norm = NormalizeName(tex.name);
        auto it = g_textures.find(norm);
        if (it != g_textures.end()) {
            if (!primary) primary = it->second;
            continue;
        }
        RwTexture* created = MakeTexture(tex);
        if (!created) continue;
        g_textures[norm] = created;
        ++stats.texturesCreated;
        if (!primary) primary = created;
    }
    if (!primary) return stats;

    PatchCtx ctx{primary, 0};
    PatchObject(static_cast<RwObject*>(rootObject), &ctx, 0);
    stats.materialsPatched = ctx.patched;
    if (ctx.patched > 0) ++stats.modelsTouched;
    return stats;
}

void Release() {
    std::lock_guard<std::mutex> lock(g_texMutex);
    g_textures.clear();
}

} // namespace CustomTxdInstall
