#pragma once

#include "dialogs/tablistwidget.h"
#include "../widgets/editbox.h"

class PlayerTabList : public Widget
{
public:
	PlayerTabList();

	void show();
	void hide();

	/** Frees the widgets hide() deferred. Safe to call every frame. */
	void teardown();

	void assemble();

	virtual void performLayout() override;
	virtual void draw(ImGuiRenderer* renderer) override;
	virtual void activateEvent(bool active) override;

private:
	/* Kotak pencarian. Menyaring daftar secara lokal: server tidak dipanggil
	   ulang, hanya baris yang sudah diambil yang disaring. */
	class SearchBox : public EditBox
	{
	public:
		SearchBox(PlayerTabList* owner) : m_owner(owner) {}
		virtual void keyboardEvent(const std::string& input) override;
	private:
		PlayerTabList* m_owner;
	};

	void rebuildRows();
	float headerHeight() const;

private:
	/* Tombol teks polos: Label yang bisa diketuk, dipakai untuk "TUTUP".
	   Button biasa menggambar kotak berisi, yang di header terlihat berat. */
	class TapLabel : public Widget
	{
	public:
		TapLabel(PlayerTabList* owner, const std::string& text, const ImColor& color, float size)
			: m_owner(owner)
		{
			m_label = new Label(text, color, false, size);
			this->addChild(m_label);
		}
		virtual void performLayout() override
		{
			m_label->performLayout();
			this->setSize(m_label->size() + ImVec2(UISettings::padding(), UISettings::padding() * 0.4f));
			m_label->setPosition((size() - m_label->size()) / 2);
		}
		virtual void touchPopEvent() override
		{
			if (m_owner) m_owner->hide();
		}
	private:
		PlayerTabList* m_owner;
		Label* m_label;
	};

private:
	TabListWidget* m_tabList;
	Label* m_lServerName;
	Label* m_lTotalPlayers;
	TapLabel* m_lClose;
	SearchBox* m_search;

	/* Dataset penuh (baris tanpa header), dipakai untuk filter pencarian. */
	std::vector<std::string> m_allRows;
	std::string m_query;
	bool pendingTeardown = false;
};
