#pragma once

class EditBox : public Widget
{
public:
	EditBox();

	/** Placeholder shown while the field is empty. */
	void setPlaceholder(const std::string& text) { m_placeholder = text; updateLabel(); }
	void setTextColor(const ImColor& color) { m_textColor = color; updateLabel(); }

	virtual void performLayout() override;
	virtual void touchPopEvent() override;
	virtual void draw(ImGuiRenderer* renderer) override;
	virtual void keyboardEvent(const std::string& input) override;

	const std::string& text() const { return m_input; }

private:
	void updateLabel();

	Label* m_label;
	std::string m_input;
	std::string m_placeholder;
	ImColor m_textColor;
	ImColor m_placeholderColor;
};
