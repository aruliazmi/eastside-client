#pragma once
//
// TXD reader for 0.3.DL models, decoded without RenderWare.
//
// The mobile GTA SA build we sit on has no IMG->CTxdStore path at all
// (CTxdStore::LoadTxd is a bare "ret"), and the engine resolves material
// textures through TextureDatabaseRuntime, which only ever holds .dat/.tmb
// databases. Driving RenderWare's dictionary reader by hand down that dead path
// is what crashed the client in v2.8.0-v2.8.6.
//
// So we decode the TXD ourselves and hand back plain RGBA pixels. The caller
// turns them into RwTextures and pins them onto the model's materials with
// RpMaterialSetTexture, which the renderer does honour.
//
// Container layout, verified against a real server TXD (0xF4DA053B.txd):
//
//   @0    TexDictionary  type=0x16 size=total
//   @12   Struct         type=0x01 size=4          <- texture count
//   @28   TextureNative  type=0x15 size=n
//   @40   Struct         type=0x01 size=n-24       <- per-texture header
//   @52   platform u32 | format u32 | name[32] | mask[32]
//   @124  rasterFormat u32 (0xNN00) | fourCC[4] | w u16 | h u16
//         | depth u8 | levels u8 | rasterType u8 | compression u8
//   @140  dataSize u32
//   @144  pixel data, then one size+data pair per mip level
//
// Note the size field: normal chunks store header+payload, but the Struct chunk
// stores only the payload length (4 for the outer Struct). The reader treats a
// size below a header as a payload length.

#include <cstdint>
#include <string>
#include <vector>

namespace CustomTxd {

struct Texture {
    std::string name;
    std::vector<uint8_t> rgba;  // top-down RGBA8, width*height*4
    int width{0};
    int height{0};
};

// Decodes every texture in a .txd image.
// Returns false when the container cannot be walked at all. Textures that fail
// to decode are skipped and counted in outSkipped.
bool Parse(const uint8_t* data, size_t size,
           std::vector<Texture>& out,
           int& outSkipped);

} // namespace CustomTxd
