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

   Tertutup:  [>>]
   Terbuka :  [<<]  ESC  TAB  ALT  SPC  F  H  2  Y  N  G

   Catatan soal tombol panah:
   Versi lama punya DUA tombol terpisah — "<<" (CButton) di dalam baris dan
   ">>" (OButton) yang memindahkan dirinya sendiri ke (-150,-150) saat bar
   terbuka. Dua akibatnya:
     1. Saat bar terbuka, ">>" pindah keluar layar tetapi slot Layout-nya
        tetap ada -> kotak kosong tanpa label di ujung kiri.
     2. Saat saya keluarkan ">>" dari children() supaya slot itu hilang, ia
        berhenti menerima event sentuh (Widget::touchEvent hanya menelusuri
        children) sehingga bar tidak bisa dibuka lagi sama sekali.

   Sekarang satu Button biasa sebagai toggle, selalu jadi anak pertama.
   Button::draw() tidak memeriksa OpenButton (beda dengan CButton/OButton),
   jadi tombol panah tetap tergambar dan tetap bisa ditekan dalam dua state.
*/
ButtonPanel::ButtonPanel()
	: Layout(Orientation::HORIZONTAL)
{
	const float fs = UISettings::fontSize() / 2;

	/* ---- toggle bar ---- */
	m_bToggle = new Button(">>", fs);
	m_bToggle->setCallback([this]() {
		OpenButton = !OpenButton;
		m_bToggle->setCaption(OpenButton ? "<<" : ">>");
		applyBarVisibility();
	});

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

	/* Toggle lebih dulu supaya berada di ujung kiri bar. */
	this->addChild(m_bToggle);
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

	/* Padding dan jarak dirapatkan: 12 tombol dalam 560px menyisakan ~34px
	   per tombol dengan spasi bawaan 15px, dan label "ESC"/"TAB"/"SPC"
	   terpotong. */
	this->setPadding(6.0f);
	this->setSpacing(4.0f);

	applyBarVisibility();
}

/* Menampilkan hanya tombol panah saat bar tertutup.

   CButton::draw() memang sudah berhenti menggambar ketika OpenButton == false,
   tetapi Widget::touchEvent tetap menelusuri SEMUA anak yang visible(), jadi
   tombol yang tak terlihat masih menerima sentuhan di tempat yang sama. Dengan
   setVisible(false) tombol-tombol itu juga dilewati saat hit-testing, dan
   Layout tidak lagi menyisakan ruang untuk mereka. */
void ButtonPanel::applyBarVisibility()
{
	for (Widget* w : children())
	{
		if (w == m_bToggle) continue;
		w->setVisible(OpenButton);
	}

	performLayout();
}

/* Panel ditampilkan lewat setVisible(true) di gui.cpp tanpa pernah memanggil
   performLayout setelah ukurannya di-set (setFixedSize hanya menyimpan size).
   Tanpa ini, tombol-tombol tetap di posisi 0,0 sampai ada yang memicu layout. */
void ButtonPanel::draw(ImGuiRenderer* renderer)
{
	if (m_needsLayout && width() > 0.0f && height() > 0.0f)
	{
		m_needsLayout = false;
		performLayout();
	}

	Widget::draw(renderer);
}

/* Layout bawaan membagi lebar ke SEMUA anak, termasuk yang invisible, jadi saat
   bar tertutup tombol panah akan melebar selebar panel. Di sini baris tombol
   ditata hanya dari anak yang terlihat, dan tombol panah selalu mendapat lebar
   tetap di ujung kiri. */
void ButtonPanel::performLayout()
{
	if (children().empty()) return;

	/* Konstruktor memanggil ini sebelum UI menetapkan ukuran panel, jadi
	   width() masih 0. Tanpa penjagaan, lebar item jadi negatif. */
	if (width() <= 0.0f || height() <= 0.0f) return;

	const float pad = 6.0f;
	const float spacing = 4.0f;
	const float toggleW = toggleWidth();

	/* Hitung berapa tombol yang benar-benar tampil. */
	int visibleCount = 1; // toggle
	for (Widget* w : children())
	{
		if (w != m_bToggle && w->visible()) visibleCount++;
	}

	const float innerW = width() - pad * 2.0f;
	const float restW = innerW - toggleW - spacing * (visibleCount - 1);
	const float itemW = (visibleCount > 1) ? (restW / (visibleCount - 1)) : 0.0f;
	const float h = height() - pad * 2.0f;

	if (h <= 0.0f) return;

	/* performLayout() milik CButton menghitung ulang ukuran dari lebar label
	   dan menimpa apa pun yang sudah di-set, jadi ukuran tetap harus dipasang
	   SESUDAH-nya. Urutan sebaliknya membuat semua tombol menyusut selebar
	   teksnya dan posisi baris jadi tidak sesuai hitungan di atas. */
	m_bToggle->performLayout();
	m_bToggle->setFixedSize(ImVec2(toggleW, h));
	if (Widget* label = m_bToggle->childCount() > 0 ? m_bToggle->childAt(0) : nullptr)
		label->setPosition((m_bToggle->size() - label->size()) / 2.0f);
	m_bToggle->setPosition(ImVec2(pad, pad));

	float x = pad + toggleW + spacing;
	for (Widget* w : children())
	{
		if (w == m_bToggle || !w->visible()) continue;
		w->performLayout();
		w->setFixedSize(ImVec2(itemW, h));
		if (Widget* label = w->childCount() > 0 ? w->childAt(0) : nullptr)
			label->setPosition((w->size() - label->size()) / 2.0f);
		w->setPosition(ImVec2(x, pad));
		x += itemW + spacing;
	}
}

float ButtonPanel::toggleWidth() const
{
	/* Cukup untuk ">>" / "<<" dengan padding; tidak ikut melebar. */
	return UISettings::fontSize() * 0.75f;
}
