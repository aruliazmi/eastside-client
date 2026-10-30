#include "custom_txd_install.h"

#include "../../game/RW/RenderWare.h"
#include "../../game/Models/BaseModelInfo.h"
#include "../../main.h"
#include "../../vendor/shadowhook/patch.h"

#include <cstring>
#include <mutex>
#include <unordered_map>

// RpMaterialSetTexture(dst, src, } calls the real C++ function by address, the same
// way every other RenderWare wrapper in this port does. The mangled symbol is
// _Z20RpMaterialSetTextureP10RpMaterialP9RwTexture
// (arm64 0x2BE620, armv7 0x217224). Declaring it extern "C" does not match that
// symbol, so the link failed.
static void* RpMaterialSetTexture(void* material, void* texture) {
    return CHook::CallFunction<void*>(
        g_libGTASA + (VER_x32 ? 0x00217224 + 1 : 0x2BE620), material, texture);
}

namespace CustomTxdInstall {
namespace {

std::mutex g_texMutex;
std::unordered_map<std::string, RwTexture*> g_textures;

// Material names are not reachable through the public RpMaterial struct in these
// headers, so textures are bound in the order the DFF declares them. A 0.3.DL skin
// TXD is normally one texture per model (Workshirt/Sleeves/Pants/... belong to
// separate DFFs), so the common case is a single texture for every material.
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

    // RwObject::type: 1 = rpATOMIC, 2 = rpCLUMP. Frames are RwFrame (type 0).
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
        // RwRasterDestroy is a function pointer too, but leaving one small
        // abandoned raster is safer than calling a slot that may be unset.
        return nullptr;
    }
    memcpy(pixels, tex.rgba.data(),
           static_cast<size_t>(tex.width) * tex.height * 4);
    RwRasterUnlock(raster);

    RwTexture* texture = RwTextureCreate(raster);
    if (!texture) return nullptr;
    RwTextureSetName(texture, tex.name.c_str());
    return texture;
}

} // namespace

Stats ApplyToModel(void* rootObject, const std::vector<CustomTxd::Texture>& textures) {
    Stats stats;
    if (!rootObject || textures.empty()) return stats;

    std::lock_guard<std::mutex> lock(g_texMutex);

    RwTexture* primary = nullptr;
    for (const auto& tex : textures) {
        if (tex.name.empty() || tex.rgba.empty()) continue;
        auto it = g_textures.find(tex.name);
        if (it != g_textures.end()) {
            if (!primary) primary = it->second;
            continue;
        }
        RwTexture* created = MakeTexture(tex);
        if (!created) continue;
        g_textures[tex.name] = created;
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
    // Textures stay for the session: materials hold raw pointers into them and
    // destroying an in-use texture was the cause of an earlier crash.
    std::lock_guard<std::mutex> lock(g_texMutex);
    g_textures.clear();
}

} // namespace CustomTxdInstall
