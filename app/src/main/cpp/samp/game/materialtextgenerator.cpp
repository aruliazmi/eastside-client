#include "../main.h"
#include "game.h"
#include "../gui/gui.h"
#include "RW/RenderWare.h"
#include "Scene.h"
#include "VisibilityPlugins.h"

extern UI* pUI;

MaterialTextGenerator::MaterialTextGenerator()
{
	m_camera = 0;
	m_frame = 0;

	SetUpScene();
}

void MaterialTextGenerator::SetUpScene()
{
	m_camera = RwCameraCreate();
	m_frame = RwFrameCreate();

	if (!m_camera) return;
	if (!m_frame) return;

    _rwObjectHasFrameSetFrame(m_camera, m_frame);
    RwCameraSetFarClipPlane(m_camera, 300.0f);
    RwCameraSetNearClipPlane(m_camera, 0.01f);
    RwV2d view = { 0.5f, 0.5f };
    RwCameraSetViewWindow(m_camera, &view);
    RwCameraSetProjection(m_camera, rwPERSPECTIVE);
    RpWorldAddCamera(Scene.m_pRpWorld, m_camera);
}

void MaterialTextGenerator::Render(const char* text, const ImVec2& size, int font_size, bool bold,
							   const ImColor& font_color, const ImColor& background_color, int text_align)
{
	if (!pUI || !pUI->renderer()) return;

	// Generate() runs from the game's Process phase, not from the ImGui frame that
	// ImGuiWrapper::render() opens. Beginning a second frame while one is already
	// active corrupts ImGuiContext (assert spam / garbage draw lists), so only open
	// our own frame when none is in flight and close it only in that case.
	ImGuiContext* ctx = ImGui::GetCurrentContext();
	bool ownFrame = (ctx && !ctx->FrameScopeActive);

	if (ownFrame) ImGui::NewFrame();

	std::string utfText = Encoding::cp2utf(text ? text : "");
	const char* info = utfText.c_str();

	pUI->renderer()->drawRect(ImVec2(0.0f, 0.0f), size, background_color, true);
	ImVec2 text_size = pUI->renderer()->calculateTextSize(info, font_size);

	ImVec2 text_pos = ImVec2(0.0f, 0.0f);
	switch (text_align)
	{
		case 0: // LEFT
			text_pos = ImVec2(0.0f, 0.0f);
			break;
		case 1: // CENTER
			text_pos = ImVec2((size.x - text_size.x) / 2, (size.y - text_size.y) / 2);
			break;
		case 2: // RIGHT
			text_pos = ImVec2(size.x - text_size.x, 0.0f);
			break;
	}

	pUI->renderer()->drawText(text_pos, font_color, info, false, (float)font_size);
	// ImGuiRenderer::drawText's 4th argument is "outlined", not "bold", so the
	// bold flag from SetObjectMaterialText was silently dropped. Fake it with a
	// 1px double-draw, which is what a bold glyph effectively looks like.
	if (bold) {
		pUI->renderer()->drawText(ImVec2(text_pos.x + 1.0f, text_pos.y), font_color, info, false, (float)font_size);
	}

	if (ownFrame) {
		ImGui::EndFrame();
		ImGui::Render();
		pUI->renderDrawData(ImGui::GetDrawData());
	}
}

const ImColor fix_color(uint32_t color)
{
	unsigned char a = (int)((color & 0xFF000000) >> 24);
	unsigned char r = (int)((color & 0x00FF0000) >> 16);
	unsigned char g = (int)((color & 0x0000FF00) >> 8);
	unsigned char b = (int)((color & 0x000000FF));

	//FLog("RGBA: 0x%X, 0x%X, 0x%X, 0x%X", r, g, b, a);
	ImColor col = ImColor(r, g, b, a);
	//FLog("color: %f, %f, %f, %f", col.Value.x, col.Value.y, col.Value.z, col.Value.w);
	return col;
}

RwTexture* MaterialTextGenerator::Generate(const char* text, int size, int font_size, bool bold, uint32_t font_color, uint32_t background_color, int alignment)
{
	int width, height;
	GetMaterialSize(size, &width, &height);
    FLog("MaterialTextGenerator::Generate");
	RwRaster* raster = RwRasterCreate(width, height, 32, rwRASTERFORMAT8888 | rwRASTERTYPECAMERATEXTURE);
    FLog("MaterialTextGenerator::Generate1");
    RwTexture* bufferTexture = RwTextureCreate(raster);
    FLog("MaterialTextGenerator::Generate2");

	if (raster && bufferTexture)
	{
        FLog("MaterialTextGenerator::Generate3");
        m_camera->frameBuffer = raster;
        FLog("MaterialTextGenerator::Generate4");
        CVisibilityPlugins::SetRenderWareCamera(m_camera);
        FLog("MaterialTextGenerator::Generate5");
		// RwCameraClear
        RwCameraClear(m_camera, reinterpret_cast<RwRGBA *>(&background_color), 3);
        FLog("MaterialTextGenerator::Generate6");
		RwCameraBeginUpdate((RwCamera*)m_camera);
		DefinedState();
        FLog("MaterialTextGenerator::Generate7");
		Render(text, ImVec2(width, height), font_size, bold, fix_color(font_color), fix_color(background_color), alignment);
        FLog("MaterialTextGenerator::Generate8");
		RwCameraEndUpdate((RwCamera*)m_camera);
	}

	return bufferTexture;
}

void MaterialTextGenerator::GetMaterialSize(int size, int* x, int* y)
{
	// SA-MP's materialsize is a direct index into this table (0..13).
	// The old code did `(size / 10) - 1`, which maps sizes 0-9 to -1 (read one entry
	// BEFORE the array) and sizes >= 140 past the end - every material text with a
	// normal size got a garbage raster width/height.
	static const int sizes[14][2] = {
			{ 32, 32 } , { 64, 32 }, { 64, 64 }, { 128, 32 }, { 128, 64 }, { 128,128 }, { 256, 32 },
			{ 256, 64 } , { 256, 128 } , { 256, 256 } , { 512, 64 } , { 512,128 } , { 512,256 } , { 512,512 }
	};

	if (size < 0) size = 0;
	if (size > 13) size = 13;

	*x = sizes[size][0];
	*y = sizes[size][1];
}