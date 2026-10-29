#pragma once

#include "main.h"

#define EXCEPTION_CHECK(env) \
	if ((env)->ExceptionCheck()) \
	{ \
		(env)->ExceptionDescribe(); \
		(env)->ExceptionClear(); \
		return; \
	}

// The game thread is created by libGTASA and is not guaranteed to be attached
// to the JVM. GetEnv() alone returns JNI_EDETACHED there and leaves the env
// pointer NULL; every Call*Method on it is a null deref. Callers of
// CJavaWrapper run on exactly that thread (CNetGame ctor, RPC handlers, touch
// callbacks), so the env must be fetched through this helper.
inline JNIEnv* WrapperGetEnv()
{
    JNIEnv* p = nullptr;
    if (javaVM->GetEnv((void**)&p, JNI_VERSION_1_6) == JNI_OK && p)
        return p;
    if (javaVM->AttachCurrentThread(&p, nullptr) == JNI_OK && p)
        return p;
    return nullptr;
}

class CJavaWrapper
{
public:
    CJavaWrapper(JNIEnv *env, jobject activity);
    ~CJavaWrapper() {};
    
    const char* GetClipboardString();
    void SetPauseState(bool pause);
    void ShowLoadingScreen();
    void HideLoadingScreen();
    void ShowTab();
    void HideTab();
    void SetTab(int id, char* names, int score, int pings);
    void ClearTab();
    
    void ShowKeyboard();
    void HideKeyboard();

	void exitGame();

    jbyteArray as_byte_array(unsigned char* buf, int len) {
        JNIEnv* p = WrapperGetEnv();
        if (!p) return nullptr;
		jbyteArray array = p->NewByteArray (len);
		p->SetByteArrayRegion (array, 0, len, reinterpret_cast<jbyte*>(buf));
		return array;
	}

    void ShowDialog(int dialogStyle, int dialogID, char* title, char* text, char* button1, char* button2);

	void ShowEditObject();
	void HideEditObject();

    jobject activity;
    jmethodID s_setPauseState;
    jmethodID s_showLoadingScreen;
    jmethodID s_hideLoadingScreen;
    jmethodID s_ShowDialog;
    jmethodID s_showTab;
    jmethodID s_setTab;
    jmethodID s_clearTab;
    jmethodID s_hideTab;
    jmethodID s_showInputLayout;
    jmethodID s_hideInputLayout;
	jmethodID s_exitGame;
	jmethodID s_showEditObject;
	jmethodID s_hideEditObject;
};
