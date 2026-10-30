#pragma once

#include <functional>

enum class ButtonIcon {
	NONE = 0,
	TOGGLE_BAR,
	ESC,
	TAB,
	ALT,
	SPC,
	ENTER_CAR,
	HORN,
	SUBMISSION,
	YES,
	NO,
	PASSENGER
};

class Button : public Widget
{
public:
	Button(const std::string& caption, float font_size = 0.0f);

	const std::string& caption() const { return m_label->text(); }
	void setCaption(const std::string& caption) { m_label->setText(caption); }

	std::function<void()> callback() { return m_callback; }
	void setCallback(const std::function<void()>& callback) { m_callback = callback; }
	
	void setColor(const ImColor& color) { m_color = color; }
	void setColorFocused(const ImColor& color) { m_colorFocused = color; }
	void setCaptionColor(const ImColor& color) { m_label->setColor(color); }

	void setIcon(ButtonIcon icon) { m_icon = icon; }
	ButtonIcon icon() const { return m_icon; }

	virtual void performLayout() override;

	virtual void draw(ImGuiRenderer* renderer) override;

	virtual void touchPopEvent() override;

private:
	Label* m_label;
	std::function<void()> m_callback;

	ImColor m_color;
	ImColor m_colorFocused;
	ButtonIcon m_icon{ButtonIcon::NONE};
};

class CButton : public Widget
{
public:
	CButton(const std::string& caption, float font_size = 0.0f);

	const std::string& caption() const { return m_label->text(); }
	void setCaption(const std::string& caption) { m_label->setText(caption); }

	std::function<void()> callback() { return m_callback; }
	void setCallback(const std::function<void()>& callback) { m_callback = callback; }

	void setColor(const ImColor& color) { m_color = color; }
	void setColorFocused(const ImColor& color) { m_colorFocused = color; }

	void setIcon(ButtonIcon icon) { m_icon = icon; }
	ButtonIcon icon() const { return m_icon; }

	virtual void performLayout() override;

	virtual void draw(ImGuiRenderer* renderer) override;

	virtual void touchPopEvent() override;

private:
	Label* m_label;
	std::function<void()> m_callback;

	ImColor m_color;
	ImColor m_colorFocused;
	ButtonIcon m_icon{ButtonIcon::NONE};
};

//>> Button
class OButton : public Widget
{
public:
	OButton(const std::string& caption, float font_size = 0.0f);

	const std::string& caption() const { return m_label->text(); }
	void setCaption(const std::string& caption) { m_label->setText(caption); }

	std::function<void()> callback() { return m_callback; }
	void setCallback(const std::function<void()>& callback) { m_callback = callback; }

	void setColor(const ImColor& color) { m_color = color; }
	void setColorFocused(const ImColor& color) { m_colorFocused = color; }

	virtual void performLayout() override;

	virtual void draw(ImGuiRenderer* renderer) override;

	virtual void touchPopEvent() override;

private:
	Label* m_label;
	std::function<void()> m_callback;

	ImColor m_color;
	ImColor m_colorFocused;
};