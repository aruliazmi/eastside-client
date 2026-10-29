#include "../../main.h"
#include "../gui.h"
#include "../../game/game.h"
#include "../../net/netgame.h"
#include "../../net/localplayer.h"
#include "../../net/netgame.h"

extern UI* pUI;
extern CNetGame* pNetGame;
extern CGame *pGame;


bool OpenButton = false;
int Tab = 0;

/* Bar tombol in-game.

   Tata letak mengikuti bar SA-MP Mobile:
     <<  ESC  TAB  ALT  SPC  F  H  2  Y  N  G

   Dua masalah di versi sebelumnya:
   1. Urutannya << TAB ESC ALT H F Y N G 2, jadi tombol yang sering dipakai
      (SPC untuk lompat) tidak ada sama sekali.
   2. m_bOpen (">>") ikut didaftarkan sebagai anak Layout, padahal OButton
      menggambar dan memindahkan dirinya sendiri ke pojok kiri-atas. Akibatnya
      Layout menyisakan satu slot kosong selebar tombol biasa.
*/
ButtonPanel::ButtonPanel()
	: Layout(Orientation::HORIZONTAL)
{
	const float fs = UISettings::fontSize() / 2;

	/* ---- kendali panel ---- */
	CButton* m_bClose = new CButton("<<", fs);
	OButton* m_bOpenLocal  = new OButton(">>", fs);

	/* ---- kendali keyboard ---- */
	CButton* m_bEsc = new CButton("ESC", fs);
	CButton* m_bTab = new CButton("TAB", fs);
	CButton* m_bAlt = new CButton("ALT", fs);
	CButton* m_bSpc = new CButton("SPC", fs);
	CButton* m_bF   = new CButton("F", fs);
	m_bH            = new CButton("H", fs);
	CButton* m_b2   = new CButton("2", fs);
	CButton* m_bY   = new CButton("Y", fs);
	CButton* m_bN   = new CButton("N", fs);
	CButton* m_bG   = new CButton("G", fs);

	/* ---- dibuat tapi tidak ditampilkan; callback tetap dipasang ---- */
	CButton* m_bP    = new CButton("+", fs);
	CButton* m_bFOOD = new CButton("FOOD", fs);
	CButton* m_bGPS  = new CButton("GPS", fs);
	CButton* m_bD    = new CButton("D", fs);
	CButton* m_bUSE  = new CButton("USE", fs);
	(void)m_bFOOD;

	/* TAB: dulu state-nya hanya naik 0->1 dan tidak pernah kembali, jadi tab
	   list hanya bisa dibuka sekali per sesi. Ambil kebenaran dari widgetnya. */
	m_bTab->setCallback([]()
	{
		if (!pUI) return;
		PlayerTabList* list = pUI->playertablist();
		if (!list) return;
		if (list->visible()) {
			list->hide();
			Tab = 0;
		} else {
			list->show();
			Tab = 1;
		}
	});

	m_bOpenLocal->setCallback([]() { OpenButton = true; });
	m_bClose->setCallback([]() { OpenButton = false; });

	/* ESC: tutup apa pun yang menutupi dunia game, dari lapisan paling atas. */
	m_bEsc->setCallback([]() {
		if (!pUI) return;
		if (pUI->playertablist() && pUI->playertablist()->visible()) {
			pUI->playertablist()->hide();
			Tab = 0;
		}
		if (pNetGame) {
			CTextDrawPool* pTextDrawPool = pNetGame->GetTextDrawPool();
			if (pTextDrawPool) {
				pTextDrawPool->SetSelectState(false, 0);
			}
		}
		pUI->chat()->setVisible(true);
	});

	/* ALT: walk. Kirim satu kali, bukan diset true selamanya. */
	m_bAlt->setCallback([]() {
		LocalPlayerKeys.bKeys[ePadKeys::KEY_WALK] = true;
	});

	/* SPC: lompat. Sebelumnya tidak ada tombol untuk ini. */
	m_bSpc->setCallback([]() {
		LocalPlayerKeys.bKeys[ePadKeys::KEY_JUMP] = true;
	});

	m_bH->setCallback([this]() {
		if (m_bH->visible())
			LocalPlayerKeys.bKeys[ePadKeys::KEY_CTRL_BACK] = true;
	});
	m_bY->setCallback([m_bY]() { if (m_bY->visible()) LocalPlayerKeys.bKeys[ePadKeys::KEY_YES] = true; });
	m_bN->setCallback([m_bN]() { if (m_bN->visible()) LocalPlayerKeys.bKeys[ePadKeys::KEY_NO] = true; });
	m_bF->setCallback([m_bF]() { if (m_bF->visible()) LocalPlayerKeys.bKeys[ePadKeys::KEY_SECONDARY_ATTACK] = true; });
	m_bP->setCallback([m_bP]() { if (m_bP->visible()) LocalPlayerKeys.bKeys[ePadKeys::KEY_SUBMISSION] = true; });
	m_bG->setCallback([m_bG]() { if (m_bG->visible()) bNeedEnterVehicle = true; });
	m_bGPS->setCallback([]() { if (pNetGame) pNetGame->SendChatCommand("/gps"); });
	m_bD->setCallback([]() { LocalPlayerKeys.bKeys[ePadKeys::KEY_SUBMISSION] = true; });
	m_bUSE->setCallback([]() { LocalPlayerKeys.bKeys[ePadKeys::KEY_ANALOG_LEFT] = true; });
	m_b2->setCallback([m_b2]() { if (m_b2->visible()) LocalPlayerKeys.bKeys[ePadKeys::KEY_SUBMISSION] = true; });

	/* Urutan tampil — sama dengan bar referensi. */
	this->addChild(m_bClose);
	this->addChild(m_bEsc);
	this->addChild(m_bTab);
	this->addChild(m_bAlt);
	this->addChild(m_bSpc);
	this->addChild(m_bF);
	this->addChild(m_bH);
	this->addChild(m_b2);
	this->addChild(m_bY);
	this->addChild(m_bN);
	this->addChild(m_bG);

	/* Padding dan jarak dirapatkan: 11 tombol dalam 560px menyisakan ~34px
	   per tombol dengan spasi bawaan 15px, dan label "ESC"/"TAB"/"SPC"
	   terpotong. 5px menghasilkan ~45px per tombol. */
	this->setPadding(6.0f);
	this->setSpacing(5.0f);

	/* m_bOpen TIDAK dijadikan anak Layout: OButton menggambar dirinya di
	   pojok kiri-atas, dan sebagai anak Layout ia menyisakan slot kosong
	   selebar tombol di ujung kiri bar. Panel memegangnya dan menggambarnya
	   sendiri lewat draw(). */
	m_bOpen = m_bOpenLocal;
}

void ButtonPanel::performLayout()
{
	Layout::performLayout();

	/* Letakkan pembuka ">>" di pojok kiri-atas, terpisah dari baris tombol.
	   OButton::draw memindahkannya ke (-150,-150) saat bar terbuka, jadi
	   posisi di sini hanya berlaku saat bar tertutup. */
	if (m_bOpen != nullptr)
	{
		m_bOpen->setPosition(ImVec2(15.0f, 15.0f));
	}
}

void ButtonPanel::draw(ImGuiRenderer* renderer)
{
	if (m_bOpen != nullptr && m_bOpen->visible())
	{
		m_bOpen->draw(renderer);
	}

	/* Layout tidak meng-override draw(); Widget::draw yang menggambar anak. */
	Widget::draw(renderer);
}
