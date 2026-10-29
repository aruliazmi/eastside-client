#include "../gui.h"
#include "../../game/game.h"
#include "../../net/netgame.h"
#include "../../main.h"


#include <iomanip>
#include <algorithm>

extern UI* pUI;
extern CGame* pGame;
extern CNetGame* pNetGame;

/* Palet tab list. Satu aksen (kuningan), sisanya netral gelap.
   Sebelumnya header hostname merah menyala dan baris tabel biru muda —
   keduanya tidak berhubungan dengan warna UI lain di klien. */
static const ImColor kAccent   = ImColor(0xD4, 0xA5, 0x48);
static const ImColor kInk      = ImColor(0xF2, 0xED, 0xE4);
static const ImColor kInkDim   = ImColor(0x8F, 0x85, 0x77);
static const ImColor kRule     = ImColor(0x3D, 0x34, 0x28);
static const ImColor kRowSel   = ImColor(0x1F, 0x1A, 0x10);
static const ImColor kSearchBg = ImColor(0x09, 0x08, 0x07, 0xE6);

PlayerTabList::PlayerTabList()
{
	m_tabList = nullptr;
	m_lServerName = nullptr;
	m_lTotalPlayers = nullptr;
	m_lClose = nullptr;
	m_search = nullptr;
}

float PlayerTabList::headerHeight() const
{
	/* Dua baris header (judul + hitungan) lalu kotak pencarian. */
	return UISettings::padding()
		+ UISettings::fontSize() * 0.72f * 2.2f
		+ UISettings::padding()
		+ UISettings::dialogListItemHeight()
		+ UISettings::padding();
}

void PlayerTabList::show()
{
	if (visible()) return;
	if (!pNetGame) return;

	/* Frame sebelumnya bisa berakhir dengan hide() (mis. lewat tombol TUTUP)
	   yang menunda penghapusan widget. Bersihkan dulu supaya show() tidak
	   menumpuk widget baru di atas sisa yang belum dibuang. */
	teardown();

	pNetGame->UpdatePlayerScoresAndPings();

	m_lServerName = new Label("", kInk, false, UISettings::fontSize() / 1.7f);
	this->addChild(m_lServerName);

	m_lTotalPlayers = new Label("", kInkDim, false, UISettings::fontSize() / 2.4f);
	this->addChild(m_lTotalPlayers);

	m_lClose = new TapLabel(this, "TUTUP", kAccent, UISettings::fontSize() / 2.4f);
	this->addChild(m_lClose);

	m_search = new SearchBox(this);
	m_search->setPlaceholder("Cari nama atau ID...");
	this->addChild(m_search);

	m_tabList = new TabListWidget();
	this->addChild(m_tabList);

	assemble();
	performLayout();

	pGame->EnableGameInput(false);
	pUI->keyboard()->setVisible(false);

	setVisible(true);
}

void PlayerTabList::hide()
{
	/* Jangan hapus widget di sini: hide() bisa datang dari touchPopEvent()
	   milik Tombol TUTUP, dan removeChild() melakukan delete pada objek yang
	   stack frame-nya sedang dieksekusi. Tandai saja, lalu teardown()
	   dikerjakan di luar jalur event (lihat UI::render). */
	pendingTeardown = true;
	setVisible(false);
	if (pGame) pGame->EnableGameInput(true);
}

void PlayerTabList::teardown()
{
	if (!pendingTeardown) return;
	pendingTeardown = false;

	for (Widget* w : std::vector<Widget*>{ m_tabList, m_lServerName, m_lTotalPlayers, m_lClose, m_search })
	{
		if (w != nullptr) this->removeChild(w);
	}

	m_tabList = nullptr;
	m_lServerName = nullptr;
	m_lTotalPlayers = nullptr;
	m_lClose = nullptr;
	m_search = nullptr;

	m_allRows.clear();
	m_query.clear();
}

std::string int_to_hex(int val)
{
	char hex[20];
	sprintf(hex, "%02X", val);
	return std::string(hex);
}

void PlayerTabList::assemble()
{
	CPlayerPool* pPlayerPool = pNetGame->GetPlayerPool();
	if (!pPlayerPool) return;
	CLocalPlayer* pLocalPlayer = pPlayerPool->GetLocalPlayer();

	int totalPlayers = pPlayerPool->GetTotalPlayers();

	m_lServerName->setText(Encoding::cp2utf(pNetGame->m_szHostName));

	std::stringstream ss;
	ss << "ONLINE: " << totalPlayers;
	m_lTotalPlayers->setText(ss.str());

	m_allRows.clear();

	if (pLocalPlayer)
	{
		ImColor lc = UI::fixcolor(pLocalPlayer->GetPlayerColorAsRGBA());
		std::string localColorTag = "{" +
			int_to_hex((int)(lc.Value.x * 255.0f)) +
			int_to_hex((int)(lc.Value.y * 255.0f)) +
			int_to_hex((int)(lc.Value.z * 255.0f)) + "}";

		std::stringstream row;
		row << localColorTag << pPlayerPool->GetLocalPlayerID() << "\t"
			<< localColorTag << pPlayerPool->GetLocalPlayerName() << "\t"
			<< localColorTag << pPlayerPool->GetLocalPlayerScore() << "\t"
			<< localColorTag << pPlayerPool->GetLocalPlayerPing();
		m_allRows.push_back(row.str());
	}

	for (int i = 0; i < MAX_PLAYERS; i++)
	{
		if (pPlayerPool->GetAt(i) && !pPlayerPool->IsPlayerNPC(i))
		{
			ImColor rc = UI::fixcolor(pPlayerPool->GetAt(i)->GetPlayerColor());
			std::string tag = "{" +
				int_to_hex((int)(rc.Value.x * 255.0f)) +
				int_to_hex((int)(rc.Value.y * 255.0f)) +
				int_to_hex((int)(rc.Value.z * 255.0f)) + "}";

			std::stringstream row;
			row << tag << i << "\t"
				<< tag << pPlayerPool->GetPlayerName(i) << "\t"
				<< tag << pPlayerPool->GetPlayerScore(i) << "\t"
				<< tag << pPlayerPool->GetPlayerPing(i);
			m_allRows.push_back(row.str());
		}
	}

	rebuildRows();
}

void PlayerTabList::rebuildRows()
{
	if (!m_tabList) return;

	m_tabList->clearItems();

	/* Header kolom. Mono kapital, redup — bukan biru muda. */
	std::stringstream data;
	data << "{8F8577}ID\t{8F8577}NAMA PEMAIN\t{8F8577}SCORE\t{8F8577}PING\n";

	std::string q = m_query;
	std::transform(q.begin(), q.end(), q.begin(), ::tolower);

	for (const std::string& row : m_allRows)
	{
		if (!q.empty())
		{
			std::string lower = row;
			std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
			if (lower.find(q) == std::string::npos) continue;
		}
		data << row << "\n";
	}

	m_tabList->assemble(data.str());
	performLayout();
}

void PlayerTabList::SearchBox::keyboardEvent(const std::string& input)
{
	EditBox::keyboardEvent(input);
	if (m_owner) {
		m_owner->m_query = input;
		m_owner->rebuildRows();
	}
}

void PlayerTabList::performLayout()
{
	if (m_tabList == nullptr) return;

	const float pad = UISettings::padding();
	const float header_height = headerHeight();

	m_tabList->setMinSize(this->minSize() - ImVec2(0.0f, header_height));
	m_tabList->setMaxSize(this->maxSize() - ImVec2(0.0f, header_height));
	m_tabList->performLayout();

	if (m_lServerName)
	{
		m_lServerName->performLayout();
		m_lServerName->setPosition(ImVec2(pad, pad));
	}

	if (m_lTotalPlayers)
	{
		m_lTotalPlayers->performLayout();
		m_lTotalPlayers->setPosition(ImVec2(
			pad,
			pad + m_lServerName->height() + 2.0f));
	}

	if (m_lClose)
	{
		m_lClose->performLayout();
		m_lClose->setPosition(ImVec2(
			m_tabList->width() - (m_lClose->width() + pad),
			pad));
	}

	if (m_search)
	{
		const float search_y = pad
			+ m_lServerName->height()
			+ m_lTotalPlayers->height()
			+ pad;

		m_search->setFixedSize(ImVec2(
			m_tabList->width() - pad * 2.0f,
			UISettings::dialogListItemHeight()));
		m_search->setPosition(ImVec2(pad, search_y));
		m_search->performLayout();
	}

	m_tabList->setPosition(ImVec2(0.0f, header_height));

	this->setSize(m_tabList->size() + ImVec2(0.0f, header_height));

	if (parent())
	{
		this->setPosition(ImVec2(
			(parent()->width() - width()) / 2,
			(parent()->height() - height()) / 2));
	}
}

void PlayerTabList::draw(ImGuiRenderer* renderer)
{
	renderer->pushClipRect(absolutePosition(), absolutePosition() + size(), true);

	/* Panel: hampir hitam, bukan hitam pekat — sama seperti dialog lain. */
	renderer->drawRect(absolutePosition(), absolutePosition() + size(),
		ImColor(0x0D, 0x0B, 0x09, 0xF2), true);

	const float pad = UISettings::padding();
	const float header_height = headerHeight();

	/* Garis pemisah tipis di bawah header, bukan blok hitam penuh. */
	renderer->drawRect(
		absolutePosition() + ImVec2(0.0f, header_height - 1.0f),
		absolutePosition() + ImVec2(size().x, header_height),
		kRule, true);

	/* Kotak pencarian: hanya garis, sama seperti isian di launcher. */
	if (m_search)
	{
		ImVec2 p0 = m_search->absolutePosition();
		renderer->drawRect(p0, p0 + m_search->size(), kSearchBg, true);
		renderer->drawRect(p0, p0 + m_search->size(), kRule, false, 1.0f);
	}

	/* Aksen kuningan di tepi kiri panel: penanda bahwa ini panel aktif. */
	renderer->drawRect(absolutePosition(),
		absolutePosition() + ImVec2(3.0f, size().y), kAccent, true);

	Widget::draw(renderer);
	renderer->popClipRect();
}

void PlayerTabList::activateEvent(bool active)
{
	if (active == false) {
		this->hide();
	}
}
