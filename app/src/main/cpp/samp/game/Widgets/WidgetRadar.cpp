//
// Created by x1y2z on 25.01.2024.
//

#include "WidgetRadar.h"
#include "vendor/shadowhook/patch.h"


void (*CWidgetRadar__Update)(CWidgetRadar* thiz);
void CWidgetRadar__Update_hook(CWidgetRadar* thiz)
{
    // Force radar to bottom-left corner (PC layout)
    thiz->m_fOriginX = 76.0f;
    thiz->m_fOriginY = 370.0f;
    thiz->m_fScaleX = 44.0f;
    thiz->m_fScaleY = 44.0f;
    thiz->m_fTapHoldTime = 999.f;

    CWidgetRadar__Update(thiz);
}

void CWidgetRadar::InjectHooks() {
    CHook::InlineHook("_ZN12CWidgetRadar6UpdateEv", &CWidgetRadar__Update_hook, &CWidgetRadar__Update);
}
