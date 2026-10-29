//
// Created by roman on 11/19/2024.
//

#include <dlfcn.h>
#include "CUtil.h"
#include "game/Textures/TextureDatabaseRuntime.h"


uintptr_t CUtil::FindLib(const char* libname)
{
    void* handle = dlopen(libname, RTLD_LAZY);
    if (handle) {
        void* symbol = dlsym(handle, "JNI_OnLoad");
        if (symbol) {
            Dl_info info;
            if (dladdr(symbol, &info) != 0) {
                return reinterpret_cast<uintptr_t>(info.dli_fbase);
            }
        }
        dlclose(handle);
    }

    return 0;
}

RwTexture* CUtil::LoadTextureFromDB(const char* dbname, const char* texture)
{
    // 'samp' is deliberately not in InitialiseRenderWare's load set: loading it
    // faults _rwOpenGLRasterSetImage with a constant 0x801000 request regardless
    // of on-disk variant (see game.cpp). GetDatabase("samp") still returns a
    // half-initialised handle whose entries array was never filled, and
    // Register + GetTexture then stream from it -> SIGSEGV 0x110 inside
    // _rwStreamReadChunkHeader (tombstone_02/03). Reject the db name outright.
    if (dbname && strcmp(dbname, "samp") == 0)
        return nullptr;

    TextureDatabaseRuntime* db_handle = TextureDatabaseRuntime::GetDatabase(dbname);
    if(!db_handle)
    {
        FLog("Error: Database not found! (%s)", dbname);
        return nullptr;
    }

    // GetDatabase() returns a NON-NULL EMPTY object for names that were never
    // loaded (server material names like 'gtasavectormap1', 'sign', ...). The
    // engine allocates the slot but leaves entries/hashOffsets null; Register +
    // GetTexture then walk that empty state - usually "texture not found", but
    // if the lazy stream file is also garbage it is SIGSEGV 0x110 in
    // _rwStreamReadChunkHeader (tombstone_05/08/01). A db that was really
    // Loaded() has its own name set; an empty placeholder does not match.
    if (!db_handle->name || strcmp(db_handle->name, dbname) != 0)
    {
        FLog("Error: Database '%s' is not loaded (empty slot) - skipping", dbname);
        return nullptr;
    }

    TextureDatabaseRuntime::Register(db_handle);

    auto tex = CUtil::GetTexture(texture);
    if(!tex)
    {
        FLog("Error: Texture (%s) not found in database (%s)", dbname, texture);
        return nullptr;
    }

    TextureDatabaseRuntime::Unregister(db_handle);

    return tex;
}

RwTexture* CUtil::GetTexture(const char* name)
{
    auto tex = TextureDatabaseRuntime::GetTexture(name);
    if (!tex)
    {
        //tex = CUtil::LoadTextureFromDB("gta3", "ahoodfence2");
        FLog("WARNING! No tex = %s", name);
        return nullptr;
    }
    ++tex->refCount;

    return tex;
}

void __fastcall CUtil::TransformPoint(RwV3d &result, const CSimpleTransform &t, const RwV3d &v)
{
    float cos_heading = cosf(t.m_fHeading);
    float sin_heading = sinf(t.m_fHeading);

    result = {
            t.m_vPosn.x + cos_heading * v.x - sin_heading * v.y,
            t.m_vPosn.y + sin_heading * v.x + cos_heading * v.y,
            v.z + t.m_vPosn.z
    };
}

float CUtil::GetDistanceBetween3DPoints(const RwV3d f, const RwV3d s)
{
    return sqrt(pow(s.x - f.x, 2) + pow(s.y - f.y, 2) + pow(s.z - f.z, 2));
}