//
// Created by x1y2z on 08.11.2023.
//

#include "Pickups.h"
#include "game.h"
#include "net/netgame.h"
#include "../vendor/shadowhook/patch.h"
#include "World.h"
#include "Pickup.h"
#include "Camera.h"

extern CNetGame* pNetGame;
void CPickups::InjectHooks() {
}

void CPickups::Update() {
    static std::array<CPickup, MAX_NUM_PICKUPS>& aPickUps = *(std::array<CPickup, MAX_NUM_PICKUPS>*)(g_libGTASA + (VER_x32 ? 0x007AFD70 : 0x991AB8));

    auto start = 620 * (CTimer::GetFrameCounter() % 32) / 32;
    auto end   = 620 * (CTimer::GetFrameCounter() % 32 + 1) / 32;
    for (auto i = start; i < end; i++) {
        auto& pickup = aPickUps[i];
        if (pickup.m_nPickupType == PICKUP_NONE)
            continue;

        if (pickup.m_nFlags.bVisible = pickup.IsVisible()) {
            if (!pickup.m_nFlags.bDisabled && !pickup.m_pObject) {
                pickup.GiveUsAPickUpObject(&pickup.m_pObject, -1);

                if (auto& obj = pickup.m_pObject; obj) {
                    CWorld::Add(obj);
                }
            }
            if (pickup.Update()) {
                // `i` is the *pool slot* that just died, not the pickup handle.
                // PickedUp() expects a GTA handle and resolves it through
                // GetIDFromGTAId(), so passing `i` made every collection look up
                // a handle that did not exist, the lookup returned -1, and the
                // guard then indexed m_dwHnd[-1] instead of reporting the pickup.
                //
                // CPickup::Update() reports through m_nReferenceIndex, which is
                // the reference the pickup was created with and the value the
                // pool stores as its GTA id.
                CPickupPool *pPickups = pNetGame->GetPickupPool();
                if (pPickups)
                    pPickups->PickedUp(pickup.m_nReferenceIndex);
            }
        } else {
            pickup.GetRidOfObjects();
        }
    }
}
