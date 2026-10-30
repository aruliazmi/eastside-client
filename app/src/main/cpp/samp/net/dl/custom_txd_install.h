#pragma once
//
// Global registry and material binder for downloaded 0.3.DL custom textures.
//
// In GTA SA mobile (libGTASA.so), the engine resolves all textures through:
//   TextureDatabaseRuntime::GetTexture(name) and CTxdStore::TxdStoreFindCB(name)
// Custom 0.3.DL TXD textures are stored in this registry and served directly
// to those engine hooks so every streamed-in ped skin, object, or vehicle
// receives its textures automatically without relying on .dat/.tmb files.

#include <cstdint>
#include <string>
#include <vector>

#include "custom_txd.h"

struct RwTexture;

namespace CustomTxdInstall {

struct Stats {
    int texturesCreated{0};
    int materialsPatched{0};
    int modelsTouched{0};
};

// Registers decoded textures into the global custom texture registry.
// Returns the count of newly created textures.
int RegisterTextures(const std::vector<CustomTxd::Texture>& textures);

// Finds a registered custom texture by name (case-insensitive).
// Returns nullptr if not found.
RwTexture* FindTexture(const char* name);

// Builds RwTextures from the decoded textures and applies them to every material
// of the given atomic/frame that names one of them.
// `rootObject` is the model's RwObject (an atomic or a frame).
Stats ApplyToModel(void* rootObject, const std::vector<CustomTxd::Texture>& textures);

// Releases textures created by RegisterTextures / ApplyToModel.
void Release();

} // namespace CustomTxdInstall
