#pragma once
//
// Puts a downloaded TXD's textures onto a custom model's materials.
//
// Why this exists: on this mobile GTA SA build the engine resolves a material's
// texture through CTxdStore::TxdStoreFindCB -> TextureDatabaseRuntime::GetTexture,
// and that database is only ever built from .dat/.tmb/.toc files. A 0.3.DL model
// ships its TXD as a plain IMG entry, so the SDK path cannot see it and the model
// renders untextured. Building RwTextures ourselves and handing them to
// RpMaterialSetTexture is the one route the renderer honours.
//
// Textures are matched to materials by name, which is how the DFF refers to them.

#include <cstdint>
#include <string>
#include <vector>

#include "custom_txd.h"

namespace CustomTxdInstall {

struct Stats {
    int texturesCreated{0};
    int materialsPatched{0};
    int modelsTouched{0};
};

// Builds RwTextures from the decoded textures and applies them to every material
// of the given atomic/frame that names one of them.
// `rootObject` is the model's RwObject (an atomic or a frame).
Stats ApplyToModel(void* rootObject, const std::vector<CustomTxd::Texture>& textures);

// Releases textures created by ApplyToModel. Call when the model is destroyed.
void Release();

} // namespace CustomTxdInstall
