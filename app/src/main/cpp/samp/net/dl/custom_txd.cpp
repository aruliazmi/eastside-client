#include "custom_txd.h"

#include <cstring>

namespace CustomTxd {
namespace {

enum : uint32_t {
    CHUNK_STRUCT        = 0x01,
    CHUNK_STRING        = 0x02,
    CHUNK_EXTENSION     = 0x03,
    CHUNK_TEXTURE       = 0x06,
    CHUNK_TEXDICTIONARY = 0x16,
    CHUNK_TEXTURENATIVE = 0x15,
};

// Raster format: high byte is the storage kind, low byte carries flags such as
// "has alpha" or "is cube map", so compare the masked part only.
enum : uint32_t {
    // The storage kind lives in the HIGH byte (0x11=DXT1, 0x12=DXT3, 0x13=DXT5,
    // 0x01..0x0A for raw formats); the low byte carries flags such as alpha.
    // Masking with 0x0F00 instead of 0xFF00 turned 0x1103 (DXT1) into 0x0100
    // (1555) and decoded raw pixels out of compressed blocks - the image came out
    // as noise over a grey void.
    RASTER_MASK       = 0xFF00,
    RASTER_1555       = 0x0100,
    RASTER_565        = 0x0200,
    RASTER_4444       = 0x0300,
    RASTER_8888       = 0x0500,
    RASTER_888        = 0x0600,
    RASTER_PAL8       = 0x0900,
    RASTER_PAL4       = 0x0A00,
    RASTER_DXT1       = 0x1100,
    RASTER_DXT3       = 0x1200,
    RASTER_DXT5       = 0x1300,
};

struct Reader {
    const uint8_t* p;
    size_t size;

    bool Has(size_t at, size_t n) const { return at + n <= size; }
    uint32_t U32(size_t at) const { uint32_t v = 0; memcpy(&v, p + at, 4); return v; }
    uint16_t U16(size_t at) const { uint16_t v = 0; memcpy(&v, p + at, 2); return v; }
    uint8_t  U8 (size_t at) const { return p[at]; }
};

// Normal chunks store header+payload; the Struct chunk in these files stores
// only the payload length. Anything smaller than a header is a payload length.
inline size_t PayloadLen(uint32_t size) {
    return (size >= 12) ? static_cast<size_t>(size) - 12 : static_cast<size_t>(size);
}

template <typename Fn>
void Walk(const Reader& r, size_t from, size_t to, Fn fn) {
    size_t off = from;
    while (off + 12 <= to) {
        const uint32_t type = r.U32(off);
        const uint32_t size = r.U32(off + 4);
        const uint32_t ver  = r.U32(off + 8);
        const size_t len  = PayloadLen(size);
        const size_t body = off + 12;
        if (body + len > to) break;
        fn(type, body, len, ver);
        off = body + len;
    }
}

inline void Put565(uint16_t c, uint8_t* out) {
    out[0] = static_cast<uint8_t>(((c >> 11) & 0x1F) * 255 / 31);
    out[1] = static_cast<uint8_t>(((c >> 5) & 0x3F) * 255 / 63);
    out[2] = static_cast<uint8_t>((c & 0x1F) * 255 / 31);
    out[3] = 255;
}

// One 4x4 DXT colour block (8 bytes) -> 16 RGBA pixels.
void ColorBlock(const uint8_t* src, uint8_t* out, bool allowAlpha) {
    const uint16_t c0 = static_cast<uint16_t>(src[0] | (src[1] << 8));
    const uint16_t c1 = static_cast<uint16_t>(src[2] | (src[3] << 8));
    uint8_t pal[4][4];
    Put565(c0, pal[0]);
    Put565(c1, pal[1]);
    if (!allowAlpha || c0 > c1) {
        for (int k = 0; k < 3; ++k) {
            pal[2][k] = static_cast<uint8_t>((2 * pal[0][k] + pal[1][k]) / 3);
            pal[3][k] = static_cast<uint8_t>((pal[0][k] + 2 * pal[1][k]) / 3);
        }
        pal[2][3] = pal[3][3] = 255;
    } else {
        for (int k = 0; k < 3; ++k) {
            pal[2][k] = static_cast<uint8_t>((pal[0][k] + pal[1][k]) / 2);
        }
        pal[2][3] = 255;
        pal[3][0] = pal[3][1] = pal[3][2] = 0;
        pal[3][3] = 0;
    }
    const uint32_t bits = static_cast<uint32_t>(src[4]) |
                          (static_cast<uint32_t>(src[5]) << 8) |
                          (static_cast<uint32_t>(src[6]) << 16) |
                          (static_cast<uint32_t>(src[7]) << 24);
    for (int i = 0; i < 16; ++i) {
        memcpy(out + i * 4, pal[(bits >> (2 * i)) & 0x3], 4);
    }
}

void DecodeDXT1(const uint8_t* src, size_t len, int w, int h, uint8_t* out) {
    const int bw = (w + 3) / 4, bh = (h + 3) / 4;
    for (int by = 0; by < bh; ++by) {
        for (int bx = 0; bx < bw; ++bx) {
            const size_t index = static_cast<size_t>(by) * bw + bx;
            if (index * 8 + 8 > len) return;
            uint8_t px[64];
            ColorBlock(src + index * 8, px, true);
            for (int y = 0; y < 4; ++y) {
                const int dy = by * 4 + y;
                if (dy >= h) break;
                for (int x = 0; x < 4; ++x) {
                    const int dx = bx * 4 + x;
                    if (dx >= w) continue;
                    memcpy(out + (static_cast<size_t>(dy) * w + dx) * 4,
                           px + (y * 4 + x) * 4, 4);
                }
            }
        }
    }
}

void DecodeDXT5(const uint8_t* src, size_t len, int w, int h, uint8_t* out) {
    const int bw = (w + 3) / 4, bh = (h + 3) / 4;
    for (int by = 0; by < bh; ++by) {
        for (int bx = 0; bx < bw; ++bx) {
            const size_t index = static_cast<size_t>(by) * bw + bx;
            if (index * 16 + 16 > len) return;
            const uint8_t* blk = src + index * 16;

            const uint8_t a0 = blk[0], a1 = blk[1];
            uint8_t apal[8];
            apal[0] = a0; apal[1] = a1;
            if (a0 > a1) {
                for (int k = 1; k <= 6; ++k) {
                    apal[k + 1] = static_cast<uint8_t>(((7 - k) * a0 + k * a1) / 7);
                }
            } else {
                for (int k = 1; k <= 4; ++k) {
                    apal[k + 1] = static_cast<uint8_t>(((5 - k) * a0 + k * a1) / 5);
                }
                apal[6] = 0; apal[7] = 255;
            }
            uint64_t abits = 0;
            for (int k = 0; k < 6; ++k) abits |= static_cast<uint64_t>(blk[2 + k]) << (8 * k);

            uint8_t px[64];
            ColorBlock(blk + 8, px, true);
            for (int i = 0; i < 16; ++i) {
                px[i * 4 + 3] = apal[(abits >> (3 * i)) & 0x7];
            }
            for (int y = 0; y < 4; ++y) {
                const int dy = by * 4 + y;
                if (dy >= h) break;
                for (int x = 0; x < 4; ++x) {
                    const int dx = bx * 4 + x;
                    if (dx >= w) continue;
                    memcpy(out + (static_cast<size_t>(dy) * w + dx) * 4,
                           px + (y * 4 + x) * 4, 4);
                }
            }
        }
    }
}

void DecodeDXT3(const uint8_t* src, size_t len, int w, int h, uint8_t* out) {
    const int bw = (w + 3) / 4, bh = (h + 3) / 4;
    for (int by = 0; by < bh; ++by) {
        for (int bx = 0; bx < bw; ++bx) {
            const size_t index = static_cast<size_t>(by) * bw + bx;
            if (index * 16 + 16 > len) return;
            const uint8_t* blk = src + index * 16;
            uint64_t abits = 0;
            for (int k = 0; k < 8; ++k) abits |= static_cast<uint64_t>(blk[k]) << (8 * k);
            uint8_t px[64];
            ColorBlock(blk + 8, px, true);
            for (int i = 0; i < 16; ++i) {
                px[i * 4 + 3] = static_cast<uint8_t>(((abits >> (4 * i)) & 0xF) * 17);
            }
            for (int y = 0; y < 4; ++y) {
                const int dy = by * 4 + y;
                if (dy >= h) break;
                for (int x = 0; x < 4; ++x) {
                    const int dx = bx * 4 + x;
                    if (dx >= w) continue;
                    memcpy(out + (static_cast<size_t>(dy) * w + dx) * 4,
                           px + (y * 4 + x) * 4, 4);
                }
            }
        }
    }
}

void DecodeUncompressed(const uint8_t* src, size_t len, int w, int h,
                        uint32_t fmt, uint8_t* out) {
    const size_t pixels = static_cast<size_t>(w) * h;
    for (size_t i = 0; i < pixels; ++i) {
        uint8_t* o = out + i * 4;
        switch (fmt) {
            case RASTER_8888:
                if ((i + 1) * 4 > len) return;
                memcpy(o, src + i * 4, 4);
                break;
            case RASTER_888:
                if ((i + 1) * 3 > len) return;
                o[0] = src[i * 3]; o[1] = src[i * 3 + 1]; o[2] = src[i * 3 + 2]; o[3] = 255;
                break;
            case RASTER_4444: {
                if ((i + 1) * 2 > len) return;
                const uint16_t v = static_cast<uint16_t>(src[i * 2] | (src[i * 2 + 1] << 8));
                o[0] = static_cast<uint8_t>(((v >> 8) & 0xF) * 17);
                o[1] = static_cast<uint8_t>(((v >> 4) & 0xF) * 17);
                o[2] = static_cast<uint8_t>((v & 0xF) * 17);
                o[3] = static_cast<uint8_t>(((v >> 12) & 0xF) * 17);
                break;
            }
            case RASTER_1555: {
                if ((i + 1) * 2 > len) return;
                const uint16_t v = static_cast<uint16_t>(src[i * 2] | (src[i * 2 + 1] << 8));
                o[0] = static_cast<uint8_t>(((v >> 10) & 0x1F) * 255 / 31);
                o[1] = static_cast<uint8_t>(((v >> 5) & 0x1F) * 255 / 31);
                o[2] = static_cast<uint8_t>((v & 0x1F) * 255 / 31);
                o[3] = (v & 0x8000) ? 255 : 0;
                break;
            }
            case RASTER_565: {
                if ((i + 1) * 2 > len) return;
                Put565(static_cast<uint16_t>(src[i * 2] | (src[i * 2 + 1] << 8)), o);
                break;
            }
            default:
                return;
        }
    }
}

} // namespace

bool Parse(const uint8_t* data, size_t size,
           std::vector<Texture>& out,
           int& outSkipped) {
    out.clear();
    outSkipped = 0;
    if (!data || size < 12) return false;

    Reader r{data, size};

    size_t dictBody = 0, dictEnd = size;
    if (r.U32(0) == CHUNK_TEXDICTIONARY) {
        const size_t len = PayloadLen(r.U32(4));
        if (12 + len <= size) { dictBody = 12; dictEnd = 12 + len; }
    }

    bool sawStruct = false;

    // Walk every TexturNative chunk in the dictionary. There is no intermediate
    // Texture chunk in this variant - verified against a real server TXD
    // (0xF4DA053B.txd), which lays out:
    //   @0   TexDictionary, @12 Struct(count), @28 TextureNative
    //        @40 Struct(header+sizes), @52 header, @144 pixel data
    Walk(r, dictBody, dictEnd, [&](uint32_t type, size_t body, size_t len, uint32_t) {
        if (type != CHUNK_TEXTURENATIVE) return;
        sawStruct = true;

        Walk(r, body, body + len, [&](uint32_t nt, size_t nbody, size_t nlen, uint32_t) {
            if (nt != CHUNK_STRUCT || nlen < 92) return;

            // Header: platform u32, format u32, name[32], mask[32],
            // rasterFormat u32, fourCC[4], width u16, height u16,
            // depth u8, levels u8, rasterType u8, compression u8,
            // dataSize u32.
            std::string name;
            {
                size_t n = 0;
                while (n < 32 && r.p[nbody + 8 + n] != 0) ++n;
                name.assign(reinterpret_cast<const char*>(r.p + nbody + 8), n);
            }

            // The raster format is the u32 right after the platform id
            // (0x1103 = DXT1 with alpha). The u32 at +72 is a D3D-ish format
            // hint and the FOURCC at +76 confirms the compression.
            const uint32_t rasterFormat = r.U32(nbody + 4);
            const uint32_t fmt = rasterFormat & RASTER_MASK;
            const uint32_t d3dFormat = r.U32(nbody + 72);
            const uint32_t fourcc = r.U32(nbody + 76);
            const int width  = r.U16(nbody + 80);
            const int height = r.U16(nbody + 82);
            const uint8_t depth = r.p[nbody + 84];
            const uint32_t dataSize = r.U32(nbody + 88);
            const size_t dataOff = nbody + 92;

            if (width <= 0 || height <= 0 || dataSize == 0) { ++outSkipped; return; }
            if (!r.Has(dataOff, dataSize)) { ++outSkipped; return; }

            Texture tex;
            tex.name = name;
            tex.width = width;
            tex.height = height;
            tex.rgba.assign(static_cast<size_t>(width) * height * 4, 0);

            const uint8_t* src = r.p + dataOff;

            static constexpr uint32_t FOURCC_DXT1 = 0x31545844; // 'DXT1'
            static constexpr uint32_t FOURCC_DXT3 = 0x33545844; // 'DXT3'
            static constexpr uint32_t FOURCC_DXT5 = 0x35545844; // 'DXT5'

            if (fourcc == FOURCC_DXT1) {
                DecodeDXT1(src, dataSize, width, height, tex.rgba.data());
            } else if (fourcc == FOURCC_DXT3) {
                DecodeDXT3(src, dataSize, width, height, tex.rgba.data());
            } else if (fourcc == FOURCC_DXT5) {
                DecodeDXT5(src, dataSize, width, height, tex.rgba.data());
            } else if (depth == 32) {
                // Direct3D 9 uncompressed 32-bit BGRA/BGRX (D3DFMT_A8R8G8B8 or D3DFMT_X8R8G8B8)
                const size_t pixels = static_cast<size_t>(width) * height;
                const bool isOpaque = (fourcc == 22 || d3dFormat == 22 || (rasterFormat & 0xF) == 2);
                for (size_t i = 0; i < pixels; ++i) {
                    if ((i + 1) * 4 > dataSize) break;
                    uint8_t* o = tex.rgba.data() + i * 4;
                    const uint8_t* p = src + i * 4;
                    o[0] = p[2]; // Red (swap from BGRA)
                    o[1] = p[1]; // Green
                    o[2] = p[0]; // Blue
                    o[3] = isOpaque ? 255 : p[3]; // Alpha
                }
            } else if (depth == 24) {
                // 24-bit BGR
                const size_t pixels = static_cast<size_t>(width) * height;
                for (size_t i = 0; i < pixels; ++i) {
                    if ((i + 1) * 3 > dataSize) break;
                    uint8_t* o = tex.rgba.data() + i * 4;
                    const uint8_t* p = src + i * 3;
                    o[0] = p[2]; // Red
                    o[1] = p[1]; // Green
                    o[2] = p[0]; // Blue
                    o[3] = 255;  // Alpha
                }
            } else {
                DecodeUncompressed(src, dataSize, width, height, fmt, tex.rgba.data());
            }

            if (!tex.name.empty()) out.push_back(std::move(tex));
            else ++outSkipped;
        });
    });

    return sawStruct;
}

} // namespace CustomTxd
