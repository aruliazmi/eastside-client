#pragma once

class ButtonPanel : public Layout
{
public:
	ButtonPanel();

	/** Draws the children plus the detached ">>" opener. */
	virtual void draw(ImGuiRenderer* renderer) override;

	/** Lays out the button row and parks the opener in the top-left corner. */
	virtual void performLayout() override;

	CButton* m_bH;
private:
	Button* m_bAlt;
	Button* m_bY;
	Button* m_bN;

	/* ">>" is not a child of the Layout: it draws itself at the screen
	   corner instead of taking a slot in the button row. Keeping it out of
	   children() is what removes the blank cell that used to appear at the
	   far left of the bar. It still has to be owned and drawn, so the panel
	   holds the pointer and draws it explicitly. */
	OButton* m_bOpen;
};
