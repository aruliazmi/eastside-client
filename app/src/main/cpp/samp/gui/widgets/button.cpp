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
	ImVec2 p0 = absolutePosition();
	ImVec2 p1 = p0 + size();
	ImDrawList* drawList = ImGui::GetBackgroundDrawList();

	renderer->drawRoundRect(p0, p1, focused() ? m_colorFocused : m_color, 4.0f, true);
	drawList->AddRect(p0, p1, ImColor(0xD4, 0xA5, 0x48, focused() ? 0xFF : 0x77), 4.0f, 0, 1.0f);

	if (m_icon == ButtonIcon::TOGGLE_BAR)
	{
		ImVec2 center = ImVec2((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f);
		float s = std::min(size().x, size().y) * 0.25f;
		ImColor iconCol = ImColor(0xD4, 0xA5, 0x48);
		if (OpenButton) {
			// Left chevron '<<'
			drawList->AddLine(center + ImVec2(2.0f, -s), center + ImVec2(-s + 2.0f, 0.0f), iconCol, 2.2f);
			drawList->AddLine(center + ImVec2(-s + 2.0f, 0.0f), center + ImVec2(2.0f, s), iconCol, 2.2f);
		} else {
			// Right chevron '>>'
			drawList->AddLine(center + ImVec2(-s - 2.0f, -s), center + ImVec2(-2.0f, 0.0f), iconCol, 2.2f);
			drawList->AddLine(center + ImVec2(-2.0f, 0.0f), center + ImVec2(-s - 2.0f, s), iconCol, 2.2f);
		}
		return;
	}

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

	ImVec2 p0 = absolutePosition();
	ImVec2 p1 = p0 + size();
	ImDrawList* drawList = ImGui::GetBackgroundDrawList();

	renderer->drawRoundRect(p0, p1, focused() ? m_colorFocused : m_color, 4.0f, true);
	drawList->AddRect(p0, p1, ImColor(0xD4, 0xA5, 0x48, focused() ? 0xFF : 0x55), 4.0f, 0, 1.0f);

	if (m_icon == ButtonIcon::NONE)
	{
		Widget::draw(renderer);
		return;
	}

	ImVec2 center = ImVec2((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f);
	float s = std::min(size().x, size().y) * 0.28f;
	ImColor iconCol = focused() ? ImColor(0xD4, 0xA5, 0x48) : ImColor(0xF2, 0xED, 0xE4);

	switch (m_icon) {
		case ButtonIcon::ESC: {
			// Cross 'X' in crimson red
			drawList->AddLine(center - ImVec2(s, s), center + ImVec2(s, s), ImColor(0xE5, 0x39, 0x35), 2.4f);
			drawList->AddLine(center + ImVec2(s, -s), center - ImVec2(s, -s), ImColor(0xE5, 0x39, 0x35), 2.4f);
			break;
		}
		case ButtonIcon::TAB: {
			// Three list bars (scoreboard)
			float w = s * 1.3f;
			drawList->AddLine(center + ImVec2(-w, -s), center + ImVec2(w, -s), iconCol, 2.0f);
			drawList->AddLine(center + ImVec2(-w, 0.0f), center + ImVec2(w, 0.0f), iconCol, 2.0f);
			drawList->AddLine(center + ImVec2(-w, s), center + ImVec2(w, s), iconCol, 2.0f);
			drawList->AddCircleFilled(center + ImVec2(-w - 3.5f, -s), 1.5f, ImColor(0xD4, 0xA5, 0x48));
			drawList->AddCircleFilled(center + ImVec2(-w - 3.5f, 0.0f), 1.5f, ImColor(0xD4, 0xA5, 0x48));
			drawList->AddCircleFilled(center + ImVec2(-w - 3.5f, s), 1.5f, ImColor(0xD4, 0xA5, 0x48));
			break;
		}
		case ButtonIcon::ALT: {
			// Walk chevron '>>'
			float hs = s * 0.7f;
			drawList->AddLine(center + ImVec2(-hs - 3.0f, -hs), center + ImVec2(-3.0f, 0.0f), iconCol, 2.0f);
			drawList->AddLine(center + ImVec2(-3.0f, 0.0f), center + ImVec2(-hs - 3.0f, hs), iconCol, 2.0f);
			drawList->AddLine(center + ImVec2(3.0f, -hs), center + ImVec2(hs + 3.0f, 0.0f), iconCol, 2.0f);
			drawList->AddLine(center + ImVec2(hs + 3.0f, 0.0f), center + ImVec2(3.0f, hs), iconCol, 2.0f);
			break;
		}
		case ButtonIcon::SPC: {
			// Jump arrow pointing up '^'
			drawList->AddLine(center + ImVec2(-s, s * 0.5f), center + ImVec2(0.0f, -s), iconCol, 2.2f);
			drawList->AddLine(center + ImVec2(0.0f, -s), center + ImVec2(s, s * 0.5f), iconCol, 2.2f);
			drawList->AddLine(center + ImVec2(0.0f, -s), center + ImVec2(0.0f, s), iconCol, 2.0f);
			break;
		}
		case ButtonIcon::ENTER_CAR: {
			// Car door / Enter icon: arrow entering box
			float w = s * 1.1f;
			drawList->AddRect(center - ImVec2(w, s), center + ImVec2(w * 0.3f, s), iconCol, 2.0f, 0, 1.8f);
			drawList->AddLine(center + ImVec2(-s * 0.5f, 0.0f), center + ImVec2(w * 1.1f, 0.0f), ImColor(0xD4, 0xA5, 0x48), 2.0f);
			drawList->AddLine(center + ImVec2(w * 0.5f, -s * 0.5f), center + ImVec2(w * 1.1f, 0.0f), ImColor(0xD4, 0xA5, 0x48), 2.0f);
			drawList->AddLine(center + ImVec2(w * 0.5f, s * 0.5f), center + ImVec2(w * 1.1f, 0.0f), ImColor(0xD4, 0xA5, 0x48), 2.0f);
			break;
		}
		case ButtonIcon::HORN: {
			// Megaphone / Horn
			drawList->AddTriangleFilled(center + ImVec2(-s, 0.0f), center + ImVec2(s * 0.5f, -s), center + ImVec2(s * 0.5f, s), iconCol);
			drawList->AddCircle(center + ImVec2(s * 0.9f, 0.0f), s * 0.6f, ImColor(0xD4, 0xA5, 0x48), 0, 1.8f);
			break;
		}
		case ButtonIcon::SUBMISSION: {
			// Lightning bolt (action '2')
			ImVec2 pts[6] = {
				center + ImVec2(-s * 0.2f, -s),
				center + ImVec2(s * 0.8f, -s),
				center + ImVec2(s * 0.1f, -s * 0.1f),
				center + ImVec2(s * 0.6f, -s * 0.1f),
				center + ImVec2(-s * 0.6f, s),
				center + ImVec2(-s * 0.1f, s * 0.2f)
			};
			drawList->AddPolyline(pts, 6, ImColor(0xD4, 0xA5, 0x48), true, 2.0f);
			break;
		}
		case ButtonIcon::YES: {
			// Green Checkmark '✓'
			drawList->AddLine(center + ImVec2(-s, 0.0f), center + ImVec2(-s * 0.2f, s), ImColor(0x4C, 0xAF, 0x50), 2.4f);
			drawList->AddLine(center + ImVec2(-s * 0.2f, s), center + ImVec2(s, -s), ImColor(0x4C, 0xAF, 0x50), 2.4f);
			break;
		}
		case ButtonIcon::NO: {
			// Red Cross 'X'
			drawList->AddLine(center - ImVec2(s * 0.85f, s * 0.85f), center + ImVec2(s * 0.85f, s * 0.85f), ImColor(0xE5, 0x39, 0x35), 2.4f);
			drawList->AddLine(center + ImVec2(s * 0.85f, -s * 0.85f), center - ImVec2(s * 0.85f, -s * 0.85f), ImColor(0xE5, 0x39, 0x35), 2.4f);
			break;
		}
		case ButtonIcon::PASSENGER: {
			// Passenger car silhouette / 'G'
			float r = s * 0.9f;
			drawList->AddCircle(center, r, iconCol, 0, 1.8f);
			drawList->AddCircleFilled(center + ImVec2(0.0f, -r * 0.3f), r * 0.35f, iconCol);
			drawList->AddLine(center + ImVec2(-r * 0.5f, r * 0.6f), center + ImVec2(r * 0.5f, r * 0.6f), iconCol, 2.0f);
			break;
		}
		default:
			Widget::draw(renderer);
			break;
	}
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