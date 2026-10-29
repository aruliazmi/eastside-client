#include "nv_event.h"

#include "main.h"
#include "game/RW/RenderWare.h"
#include "game/game.h"
#include "net/netgame.h"
#include <list>
#include <queue>

static std::mutex g_EventMutex;
static std::queue<NVEvent> g_pEvents;

static void NVEventInsertNewest(NVEvent& ev)
{
	std::lock_guard<std::mutex> lock(g_EventMutex);
	g_pEvents.push(ev);
}

bool NVEventGetNextEvent(NVEvent* ev)
{
	std::lock_guard<std::mutex> lock(g_EventMutex);

	if (g_pEvents.empty())
		return false;

	auto& pPopped = g_pEvents.front();
	memcpy(ev, &pPopped, sizeof(NVEvent));
	g_pEvents.pop();

	return true;
}

extern "C"
{
	// MotionEvent constants are cached for the life of the process, but FindClass
	// returns a *local* reference. Cached as-is it is use-after-free once this
	// JNI call returns. Wrap in NewGlobalRef so the jclass stays valid.
	static jclass KeyCode_class = nullptr;
	static int ACTION_DOWN = 0, ACTION_UP = 0, ACTION_POINTER_DOWN = 0,
	           ACTION_POINTER_UP = 0, ACTION_CANCEL = 0, ACTION_MASK = 0,
	           ACTION_POINTER_INDEX_MASK = 0, ACTION_POINTER_ID_SHIFT = 0;

	static void InitMotionEventCache(JNIEnv* env)
	{
		if (KeyCode_class) return;

		jclass local = env->FindClass("android/view/MotionEvent");
		if (!local) return;
		KeyCode_class = (jclass)env->NewGlobalRef(local);
		env->DeleteLocalRef(local);

		jfieldID ACTION_DOWN_id = env->GetStaticFieldID(KeyCode_class, "ACTION_DOWN", "I");
		jfieldID ACTION_UP_id = env->GetStaticFieldID(KeyCode_class, "ACTION_UP", "I");
		jfieldID ACTION_POINTER_DOWN_id = env->GetStaticFieldID(KeyCode_class, "ACTION_POINTER_DOWN", "I");
		jfieldID ACTION_POINTER_UP_id = env->GetStaticFieldID(KeyCode_class, "ACTION_POINTER_UP", "I");
		jfieldID ACTION_CANCEL_id = env->GetStaticFieldID(KeyCode_class, "ACTION_CANCEL", "I");
		jfieldID ACTION_POINTER_INDEX_SHIFT_id = env->GetStaticFieldID(KeyCode_class, "ACTION_POINTER_INDEX_SHIFT", "I");
		jfieldID ACTION_POINTER_INDEX_MASK_id = env->GetStaticFieldID(KeyCode_class, "ACTION_POINTER_INDEX_MASK", "I");
		jfieldID ACTION_MASK_id = env->GetStaticFieldID(KeyCode_class, "ACTION_MASK", "I");

		ACTION_DOWN = env->GetStaticIntField(KeyCode_class, ACTION_DOWN_id);
		ACTION_UP = env->GetStaticIntField(KeyCode_class, ACTION_UP_id);
		ACTION_POINTER_DOWN = env->GetStaticIntField(KeyCode_class, ACTION_POINTER_DOWN_id);
		ACTION_POINTER_UP = env->GetStaticIntField(KeyCode_class, ACTION_POINTER_UP_id);
		ACTION_CANCEL = env->GetStaticIntField(KeyCode_class, ACTION_CANCEL_id);
		ACTION_MASK = env->GetStaticIntField(KeyCode_class, ACTION_MASK_id);
		ACTION_POINTER_INDEX_MASK = env->GetStaticIntField(KeyCode_class, ACTION_POINTER_INDEX_MASK_id);
		ACTION_POINTER_ID_SHIFT = env->GetStaticIntField(KeyCode_class, ACTION_POINTER_INDEX_SHIFT_id);
	}

	JNIEXPORT jboolean JNICALL Java_com_nvidia_devtech_NvEventQueueActivity_customMultiTouchEvent(JNIEnv* env, jobject thiz, jint action,
		jint pointer, jint x1, jint y1, jint x2, jint y2, jint x3, jint y3)
	{
		InitMotionEventCache(env);

		{
			NVEvent ev;

			ev.m_type = NV_EVENT_MULTITOUCH;

			if (action == ACTION_UP)
			{
				ev.m_data.m_multi.m_action = NV_MULTITOUCH_UP;
			}
			else if (action == ACTION_DOWN)
			{
				ev.m_data.m_multi.m_action = NV_MULTITOUCH_DOWN;
			}
			else if (action == ACTION_POINTER_DOWN)
			{
				ev.m_data.m_multi.m_action = NV_MULTITOUCH_DOWN;
			}
			else if (action == ACTION_POINTER_UP)
			{
				ev.m_data.m_multi.m_action = NV_MULTITOUCH_UP;
			}
			else if (action == ACTION_CANCEL)
			{
				ev.m_data.m_multi.m_action = NV_MULTITOUCH_CANCEL;
			}
			else
			{
				ev.m_data.m_multi.m_action = NV_MULTITOUCH_MOVE;
			}

			ev.m_data.m_multi.m_action =
				(NVMultiTouchEventType)(ev.m_data.m_multi.m_action | (pointer << NV_MULTITOUCH_POINTER_SHIFT));
			ev.m_data.m_multi.m_x1 = x1;
			ev.m_data.m_multi.m_y1 = y1;
			ev.m_data.m_multi.m_x2 = x2;
			ev.m_data.m_multi.m_y2 = y2;
			ev.m_data.m_multi.m_x3 = x3;
			ev.m_data.m_multi.m_y3 = y3;
			NVEventInsertNewest(ev);
		}

		return JNI_TRUE;
	}
}