#include "../main.h"
#include "../game/game.h"
#include "netgame.h"

extern CGame* pGame;
extern CNetGame* pNetGame;


CTextDrawPool::CTextDrawPool()
{
    for (int i = 0; i < MAX_TEXT_DRAWS; i++) {
        m_pTextDraw[i] = nullptr;
        m_bSlotState[i] = false;
    }

    ResetTextDrawTextures();

    // ====
    m_bSelectState = false;
    m_dwHoverColor = 0;
    m_wClickedTextDrawID = 0xFFFF;
}

CTextDrawPool::~CTextDrawPool()
{
    for (int i = 0; i < MAX_TEXT_DRAWS; i++) {
        Delete(i);
    }
}

void CTextDrawPool::New(uint16_t wTextDrawID, TEXT_DRAW_TRANSMIT* pTextDrawTransmit, const char* szText)
{
    if (m_pTextDraw[wTextDrawID]) {
        Delete(wTextDrawID);
    }

    CTextDraw* pTextDraw = new CTextDraw(pTextDrawTransmit, szText);
    if (pTextDraw == nullptr) return;

    m_pTextDraw[wTextDrawID] = pTextDraw;
    m_bSlotState[wTextDrawID] = true;
}

void CTextDrawPool::Delete(uint16_t wTextDrawID)
{
    if (m_pTextDraw[wTextDrawID]) {
        delete m_pTextDraw[wTextDrawID];
        m_pTextDraw[wTextDrawID] = nullptr;
        m_bSlotState[wTextDrawID] = false;
    }
}

void CTextDrawPool::Draw()
{
    for (int i = 0; i < MAX_TEXT_DRAWS; i++)
    {
        if (m_bSlotState[i]) {
            m_pTextDraw[i]->Draw();
        }
    }
}

void CTextDrawPool::DrawImage()
{
    /*for (int i = 0; i < MAX_TEXT_DRAWS; i++)
    {
        if (m_bSlotState[i]) {
            m_pTextDraw[i]->DrawImage();
        }
    }*/
}

// ========

void CTextDrawPool::SetSelectState(bool bState, uint32_t dwColor)
{
    if (bState) {
        m_bSelectState = true;
        m_dwHoverColor = (((dwColor << 16) | dwColor & 0xFF00) << 8) | (((dwColor >> 16) | dwColor & 0xFF0000) >> 8);
        m_wClickedTextDrawID = 0xFFFF;
        pGame->DisplayHUD(false);
        pGame->FindPlayerPed()->TogglePlayerControllable(false);
    }
    else {
        m_bSelectState = false;
        m_dwHoverColor = 0;
        m_wClickedTextDrawID = 0xFFFF;
        pGame->DisplayHUD(true);
        pGame->FindPlayerPed()->TogglePlayerControllable(true);

        SendClick();

        for (int i = 0; i < MAX_TEXT_DRAWS; i++)
        {
            if (m_bSlotState[i] && m_pTextDraw[i]) {
                CTextDraw* pTextDraw = m_pTextDraw[i];
                pTextDraw->m_bHovered = false;
                pTextDraw->m_dwHoverColor = 0;
            }
        }
    }
}

void CTextDrawPool::SendClick()
{
    RakNet::BitStream bsClick;
    bsClick.Write(m_wClickedTextDrawID);
    pNetGame->GetRakClient()->RPC(&RPC_ClickTextDraw, &bsClick, HIGH_PRIORITY, RELIABLE_ORDERED, 0, false, UNASSIGNED_NETWORK_ID, nullptr);
}

bool CTextDrawPool::onTouchEvent(int type, bool multi, int x, int y)
{
    // A server that enabled SELECT_TEXT_DRAWS makes these textdraws
    // clickable. The old implementation captured EVERY touch in that mode:
    //   - returns false for any hit   -> caller sees "handled" and calls
    //                                    AND_TouchEvent(1,0,0,0), i.e. a fake
    //                                    release at (0,0)
    //   - falls through to `return true` for every miss
    // Either way AND_TouchEvent() never received the real finger position, so
    // the camera and the touch controls were both dead: the player could not
    // look around and could not even jump to reach the checkpoint.
    //
    // It also sent RPC_ClickTextDraw with wClickedTextDrawID = 0xFFFF, which is
    // not a valid textdraw id.
    //
    // The contract must be: report "handled" ONLY when a selectable textdraw was
    // actually hit. Anything else lets the event continue to the game.
    if (m_bSelectState == false) return false;

    for (int i = 0; i < MAX_TEXT_DRAWS; i++)
    {
        if (!m_bSlotState[i] || !m_pTextDraw[i]) continue;

        CTextDraw* pTextDraw = m_pTextDraw[i];
        pTextDraw->m_bHovered = false;
        pTextDraw->m_dwHoverColor = 0;

        if (!pTextDraw->m_TextDrawData.byteSelectable) continue;

        // Only react when the point really is inside this textdraw's box.
        if (!IsPointInRect((float)x, (float)y, &(pTextDraw->m_rectArea))) continue;

        switch (type)
        {
            case 2: // push: swallow so the camera does not start rotating
                m_wClickedTextDrawID = (uint16_t)i;
                return true;

            case 1: // release: this is the actual click
                m_wClickedTextDrawID = (uint16_t)i;
                SendClick();
                return true;

            default:
                break;
        }
        return true;
    }

    // Nothing hit: never consume the event.
    return false;
}

void CTextDrawPool::SnapshotProcess()
{
    for (int i = 0; i < MAX_TEXT_DRAWS; i++)
    {
        if (m_bSlotState[i] && m_pTextDraw[i]) {
            m_pTextDraw[i]->SnapshotProcess();
        }
    }
}