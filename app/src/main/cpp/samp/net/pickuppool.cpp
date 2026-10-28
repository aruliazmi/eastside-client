#include "../main.h"
#include "../game/game.h"
#include "netgame.h"

extern CGame *pGame;
extern CNetGame *pNetGame;


CPickupPool::CPickupPool()
{
	memset(m_Pickups, 0, sizeof(m_Pickups));
	m_iPickupCount = 0;

	for (int i = 0; i < MAX_PICKUPS; i++)
	{
		m_dwHnd[i] = 0;
		m_iTimer[i] = 0;
		m_dwGTAId[i] = 0xFFFFFFFF;
		memset(&m_droppedWeapon[i], 0, sizeof(DROPPED_WEAPON));
	}
}

CPickupPool::~CPickupPool()
{
	for (int i = 0; i < MAX_PICKUPS; i++)
	{
		// 0 means "no pickup" and 0xFFFFFFFF is the invalid sentinel. Handing
		// either to 0x0215 asks the game to destroy a handle it never created.
		if (m_dwHnd[i] != 0 && m_dwHnd[i] != 0xFFFFFFFF)
		{
			ScriptCommand(&destroy_pickup, m_dwHnd[i]);
			m_dwHnd[i] = 0;
		}
	}
}

/*
 * Pickup collection is reported from CPickups::Update(), which is the only
 * place that knows *which* slot the player walked over: the game clears that
 * slot's m_nPickupType, so `i` there is the dying slot, not the handle.
 *
 * 0x0214 (is_pickup_picked_up) cannot replace that. It takes no argument and
 * only answers "did the player collect anything this frame", so scanning the
 * pool with it would report the first live slot instead of the collected one.
 */
void CPickupPool::Process()
{
	for (int i = 0; i < MAX_PICKUPS; i++)
	{
		if (m_iTimer[i] > 0)
			m_iTimer[i]--;
	}
}

void CPickupPool::New(PICKUP *pPickup, int iPickup)
{
	if (!pPickup) return;
	if (m_iPickupCount >= MAX_PICKUPS || iPickup < 0 || iPickup >= MAX_PICKUPS) return;

	if (m_dwHnd[iPickup] != 0 && m_dwHnd[iPickup] != 0xFFFFFFFF)
	{
		ScriptCommand(&destroy_pickup, m_dwHnd[iPickup]);
		m_dwHnd[iPickup] = 0;
	}

	memcpy(&m_Pickups[iPickup], pPickup, sizeof(PICKUP));
	m_droppedWeapon[iPickup].bDroppedWeapon = false;
	m_droppedWeapon[iPickup].PlayerID = -1;
	m_iTimer[iPickup] = 0;

	int dwGTAId = -1;
	uintptr_t hPickup = pGame->CreatePickup(pPickup->iModel, pPickup->iType,
		pPickup->fX, pPickup->fY, pPickup->fZ, &dwGTAId);

	// A failed creation must not be registered: leaving the slot populated makes
	// the pool believe the game created a pickup that it did not.
	if (hPickup == 0 || hPickup == 0xFFFFFFFF)
	{
		memset(&m_Pickups[iPickup], 0, sizeof(PICKUP));
		m_dwHnd[iPickup] = 0;
		m_dwGTAId[iPickup] = 0xFFFFFFFF;
		return;
	}

	m_dwHnd[iPickup] = hPickup;
	m_dwGTAId[iPickup] = dwGTAId;
	m_iPickupCount++;
}

void CPickupPool::Destroy(int iPickup)
{
	if (m_iPickupCount <= 0 || iPickup < 0 || iPickup >= MAX_PICKUPS) return;

	if (m_dwHnd[iPickup] != 0 && m_dwHnd[iPickup] != 0xFFFFFFFF)
	{
		ScriptCommand(&destroy_pickup, m_dwHnd[iPickup]);
		m_dwHnd[iPickup] = 0;
		m_iTimer[iPickup] = 0;
		m_dwGTAId[iPickup] = 0xFFFFFFFF;
		memset(&m_droppedWeapon[iPickup], 0, sizeof(DROPPED_WEAPON));
		memset(&m_Pickups[iPickup], 0, sizeof(PICKUP));
		if (m_iPickupCount > 0) m_iPickupCount--;
	}
}

int CPickupPool::GetIDFromGTAId(int dwGTAId)
{
	if (dwGTAId == -1 || dwGTAId == 0xFFFFFFFF)
		return -1;

	for (int i = 0; i < MAX_PICKUPS; i++)
	{
		if (m_dwGTAId[i] == dwGTAId &&
			m_dwHnd[i] != 0 && m_dwHnd[i] != 0xFFFFFFFF)
		{
			return i;
		}
	}

	return -1;
}

void CPickupPool::PickedUp(int dwGTAId)
{
	if (dwGTAId == -1 || dwGTAId == 0xFFFFFFFF) return;

	// The old guard compared the lookup result against 0xFFFFFFFF, but
	// GetIDFromGTAId returns -1 on failure. A missed lookup therefore became
	// slot -1 and indexed m_dwHnd[-1].
	int PickupID = GetIDFromGTAId(dwGTAId);
	if (PickupID < 0 || PickupID >= MAX_PICKUPS) return;
	if (m_dwHnd[PickupID] == 0 || m_dwHnd[PickupID] == 0xFFFFFFFF) return;
	if (m_iTimer[PickupID] != 0) return;
	if (m_droppedWeapon[PickupID].bDroppedWeapon) return;

	RakNet::BitStream bsPickup;
	bsPickup.Write(PickupID);

	if (pNetGame && pNetGame->GetRakClient())
	{
		pNetGame->GetRakClient()->RPC(&RPC_PickedUpPickup, &bsPickup,
			HIGH_PRIORITY, RELIABLE_ORDERED, 0, false, UNASSIGNED_NETWORK_ID, nullptr);
	}

	m_iTimer[PickupID] = 15;
}
