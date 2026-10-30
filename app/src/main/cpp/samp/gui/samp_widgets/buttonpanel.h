#pragma once

class ButtonPanel : public Layout
{
public:
	ButtonPanel();

	/** Lays out only the visible children; the toggle keeps a fixed width. */
	virtual void performLayout() override;

	/** Emits the deferred first layout once the panel has a size. */
	virtual void draw(ImGuiRenderer* renderer) override;

	CButton* m_bH;
private:
	/** Shows the key buttons only while the bar is open. */
	void applyBarVisibility();
	float toggleWidth() const;
	bool m_needsLayout = true;

	Button* m_bAlt;
	Button* m_bY;
	Button* m_bN;

	/* Satu tombol pembuka/penutup bar, bukan dua.

	   Versi sebelumnya punya "<<" (CButton) dan ">>" (OButton) terpisah.
	   OButton memindahkan dirinya sendiri ke (-150,-150) saat bar terbuka,
	   sehingga slot Layout-nya kosong — itulah kotak tanpa label di ujung
	   kiri bar. Ketika saya keluarkan dari children() supaya slot itu hilang,
	   tombolnya berhenti menerima event sentuh dan bar tidak bisa dibuka lagi.

	   Sekarang: satu Button biasa yang caption-nya berganti ">>" / "<<".
	   Button::draw tidak memeriksa OpenButton, jadi selalu tergambar dan
	   selalu bisa ditekan. */
	Button* m_bToggle;
};
