#include "../gui.h"

extern UI* pUI;

EditBox::EditBox()
{
	m_textColor = ImColor(0xF2, 0xED, 0xE4);
	m_placeholderColor = ImColor(0x87, 0x7C, 0x6B);

	m_label = new Label(" ", m_placeholderColor, false, UISettings::fontSize() / 2);
	this->addChild(m_label);
}

void EditBox::updateLabel()
{
	// Placeholder saat kosong, isi saat ada teks. Dulu labelnya selalu putih
	// dan kosong, jadi kotak pencarian tidak punya petunjuk apa pun.
	if (m_input.empty()) {
		m_label->setColor(m_placeholderColor);
		m_label->setText(m_placeholder.empty() ? " " : m_placeholder);
	} else {
		m_label->setColor(m_textColor);
		m_label->setText(Encoding::cp2utf(m_input));
	}
}

void EditBox::performLayout()
{
	m_label->performLayout();

	m_label->setPosition(ImVec2(
		UISettings::padding(),

		(height() - m_label->height()) / 2
	));
}

void EditBox::draw(ImGuiRenderer* renderer)
{
	/* Kotak isian: garis tipis dengan sudut membulat kecil, senada dengan
	   bar tombol. Sebelumnya garis 2dp abu-abu tanpa radius. */
	renderer->drawRoundRect(
		absolutePosition() + ImVec2(1.0f, 1.0f),
		(absolutePosition() + size()) - ImVec2(1.0f, 1.0f),
		ImColor(0x3D, 0x34, 0x28), 3.0f, false, 1.0f);

	Widget::draw(renderer);
}

void EditBox::touchPopEvent()
{
	pUI->keyboard()->show(this);
}

void EditBox::keyboardEvent(const std::string& input)
{
	m_input = input;
	updateLabel();
}
