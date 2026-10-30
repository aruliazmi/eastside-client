//
// Created by x1y2z on 01.07.2023.
//

#include "TxdStore.h"
#include "../vendor/shadowhook/patch.h"

int32 CTxdStore::GetNumRefs(int32 index){
    return CHook::CallFunction<int32>(g_libGTASA + (VER_x32 ? 0x5D3E34 + 1 : 0x6F8DF4), index);
}

void CTxdStore::RemoveTxd(int32 index) {
    CHook::CallFunction<void>(g_libGTASA + (VER_x32 ? 0x5D40B8 + 1 : 0x6F9130), index);
}

void CTxdStore::InjectHooks() {
  //  CHook::Redirect(g_libGTASA, 0x0055BF14, &CTxdStore::GetNumRefs);
}

int32 CTxdStore::FindTxdSlot(const char *name) {
    return CHook::CallFunction<int32>(g_libGTASA + (VER_x32 ? 0x005D3EB0 + 1 : 0x6F8EA0), name);
}

int32 CTxdStore::FindTxdSlot(uint32 hash) {
    assert("NO x64 call");
}

int32 CTxdStore::AddTxdSlot(const char *name, const char *dbName, bool keepCPU) {
    return CHook::CallFunction<int32>(g_libGTASA + (VER_x32 ? 0x005D3B84 + 1 : 0x6F8A68), name, dbName, keepCPU);
}

// Allocates the RwTexDictionary for a slot (engine @0x6F90A8) and stores it in
// the pool entry. Needed because the mobile build never populates a slot's
// dictionary from an IMG on its own.
void CTxdStore::Create(int32 index) {
    CHook::CallFunction<void>(g_libGTASA + (VER_x32 ? 0x005D41B4 + 1 : 0x6F90A8), index);
}

// Returns the RwTexDictionary a slot currently holds, or NULL.
void* CTxdStore::GetTxd(int32 index) {
    if (!ms_pTxdPool) return nullptr;
    TxdDef* def = ms_pTxdPool->GetAt(index);
    return def ? reinterpret_cast<void*>(def->m_pRwDictionary) : nullptr;
}

void CTxdStore::Initialise() {
    CHook::CallFunction<void>(g_libGTASA + (VER_x32 ? 0x005D3A90 + 1 : 0x6F8928));
}

void CTxdStore::PushCurrentTxd() {

    CHook::CallFunction<void>(g_libGTASA + (VER_x32 ? 0x005D41D4 + 1 : 0x6F92A8));
}

void CTxdStore::PopCurrentTxd() {
    CHook::CallFunction<void>(g_libGTASA + (VER_x32 ? 0x005D4214 + 1 : 0x6F92D4));
}

void CTxdStore::SetCurrentTxd(int32 index) {
    CHook::CallFunction<void>(g_libGTASA + (VER_x32 ? 0x5D4144 + 1 : 0x6F91EC), index, nullptr);
}
