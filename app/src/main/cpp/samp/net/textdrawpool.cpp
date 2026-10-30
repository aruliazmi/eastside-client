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
    m_wPressedTextDrawID = 0xFFFF;
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
        // Do NOT disable the HUD or the player controls here.
        //
        // Vanilla SA-MP does this because on PC the mouse is needed to point at
        // the textdraw. On a phone it disables the on-screen sticks and buttons,
        // so the player cannot move or look at all while the selection is
        // active - which /gps triggers. The player then reports "the screen
        // stopped responding". Selecting a textdraw must cost a tap, not the
        // whole control scheme.
    }
    else {
        m_bSelectState = false;
        m_dwHoverColor = 0;
        m_wClickedTextDrawID = 0xFFFF;

        // SendClick() used to be called here with 0xFFFF as the id (no textdraw
        // was actually clicked), which the server saw as a click on a
        // non-existent textdraw. Only send when a real id is pending.
        if (m_wClickedTextDrawID != 0xFFFF) {
            SendClick();
        }

        for (int i = 0; i < MAX_TEXT_DRAWS; i++) {
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
    // A server that enabled SELECT_TEXT_DRAWS makes these textdraws clickable.
    //
    // The implementation must NOT consume the touch. A /gps map is drawn as a
    // full-screen selectable textdraw, so "swallow touches that hit a textdraw"
    // means swallowing every touch on the screen - including the movement stick
    // and the action buttons. The player then cannot move or look at all, which
    // is exactly the reported symptom, and pressing TAB+"close" only helped
    // because that path calls SetSelectState(false) and clears the flag.
    //
    // So: detect the click, send RPC_ClickTextDraw, and let the event continue
    // to AND_TouchEvent() untouched. A tap on the map costs a tap; it must not
    // cost the whole control scheme.
    //
    // A click is only reported when the finger went down AND up on the same
    // selectable textdraw, so dragging the camera across the map does not fire
    // a click.
    if (m_bSelectState == false) {
        m_wPressedTextDrawID = 0xFFFF;
        return false;
    }

    if (type == 2) { // push: remember what we started on, send nothing yet
        m_wPressedTextDrawID = 0xFFFF;
        for (int i = 0; i < MAX_TEXT_DRAWS; i++) {
            if (!m_bSlotState[i] || !m_pTextDraw[i]) continue;
            CTextDraw* pTextDraw = m_pTextDraw[i];
            if (!pTextDraw->m_TextDrawData.byteSelectable) continue;
            if (!IsPointInRect((float)x, (float)y, &(pTextDraw->m_rectArea))) continue;
            m_wPressedTextDrawID = (uint16_t)i;
            break;
        }
        return false;
    }

    if (type == 1) { // release: a click needs push and release on the same box
        const uint16_t pressed = m_wPressedTextDrawID;
        m_wPressedTextDrawID = 0xFFFF;
        if (pressed == 0xFFFF) return false;

        CTextDraw* pTextDraw = m_pTextDraw[pressed];
        if (!pTextDraw || !m_bSlotState[pressed]) return false;
        if (!IsPointInRect((float)x, (float)y, &(pTextDraw->m_rectArea))) return false;

        m_wClickedTextDrawID = pressed;
        SendClick();
        return false;
    }

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