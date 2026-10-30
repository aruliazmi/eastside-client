#include "../../main.h"
#include "..//gui.h"
#include "button.h"

extern bool OpenButton;

Button::Button(const std::string& caption, float font_size)
{
	m_callback = nullptr;

	m_label = new Label(caption, ImColor(1.0f, 1.0f, 1.0f), false, font_size);
	this->addChild(m_label);

	m_color = UISettings::buttonColor();
	m_colorFocused = UISettings::buttonFocusedColor();
}

void Button::performLayout()
{
	float padding = UISettings::padding();

	m_label->performLayout();
	this->setSize(m_label->size() + ImVec2(padding * 2, padding / 2 * 2));

	m_label->setPosition((size() - m_label->size()) / 2);
}

void Button::draw(ImGuiRenderer* renderer)
{
	/* Sama seperti CButton: kotak gelap semi-transparan bersudut membulat.
	   Button sengaja TIDAK memeriksa OpenButton — dipakai sebagai toggle bar
	   yang harus selalu terlihat dan selalu bisa ditekan. */
	renderer->drawRoundRect(absolutePosition(), absolutePosition() + size(),
		focused() ? m_colorFocused : m_color, 3.0f, true);

	Widget::draw(renderer);
}

void Button::touchPopEvent()
{
	if (m_callback) m_callback();
}


//============== Custom Button=========================//
CButton::CButton(const std::string& caption, float font_size)
{
	m_callback = nullptr;

	m_label = new Label(caption, ImColor(1.0f, 1.0f, 1.0f), false, font_size);
	this->addChild(m_label);

	m_color = UISettings::buttonColor();
	m_colorFocused = UISettings::buttonFocusedColor();
}

void CButton::performLayout()
{
	float padding = UISettings::padding();

	m_label->performLayout();
	this->setSize(m_label->size() + ImVec2(padding * 2, padding / 2 * 2));

	m_label->setPosition((size() - m_label->size()) / 2);
}

void CButton::draw(ImGuiRenderer* renderer)
{
	if (OpenButton == false) return;

	/* Kotak gelap semi-transparan dengan sudut membulat kecil, sama seperti
	   bar tombol SA-MP Mobile. Sebelumnya kotak pekat dengan outline 2dp
	   warna penuh, sehingga semua tombol terbaca sama penting. */
	renderer->drawRoundRect(absolutePosition(), absolutePosition() + size(),
		focused() ? m_colorFocused : m_color, 3.0f, true);

	Widget::draw(renderer);
}

void CButton::touchPopEvent()
{
	if (m_callback) m_callback();
}
//=======================================//

//======== >> Button ====================//
OButton::OButton(const std::string& caption, float font_size)
{
	m_callback = nullptr;

	m_label = new Label(caption, ImColor(1.0f, 1.0f, 1.0f), false, font_size);
	this->addChild(m_label);

	m_color = UISettings::buttonColor();
	m_colorFocused = UISettings::buttonFocusedColor();
}

void OButton::performLayout()
{
	float padding = UISettings::padding();

	m_label->performLayout();
	this->setSize(m_label->size() + ImVec2(padding * 2, padding / 2 * 2));

	m_label->setPosition((size() - m_label->size()) / 2);
	//m_label->setPosition((m_label->size()) / 2);

}

void OButton::draw(ImGuiRenderer* renderer)
{
	if (OpenButton == true)
	{
		//Set >> to hide position
		this->setPosition(ImVec2(-150.0f, -150.0f));
		return;
	}

	renderer->drawRoundRect(absolutePosition(), absolutePosition() + size(),
		focused() ? m_colorFocused : m_color, 3.0f, true);

	Widget::draw(renderer);

	//Set >> to position
	this->setPosition(ImVec2(15.0f, 15.0f));
}

void OButton::touchPopEvent()
{
	if (m_callback) m_callback();
}
//======================================//