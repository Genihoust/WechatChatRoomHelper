package com.zdy.project.wechat_chatroom_helper.wechat.plugins.hook.adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.zdy.project.wechat_chatroom_helper.LogUtils
import com.zdy.project.wechat_chatroom_helper.PageType
import com.zdy.project.wechat_chatroom_helper.wechat.manager.DrawableMaker
import com.zdy.project.wechat_chatroom_helper.wechat.plugins.RuntimeInfo
import com.zdy.project.wechat_chatroom_helper.wechat.plugins.classparser.ConversationReflectFunction
import com.zdy.project.wechat_chatroom_helper.wechat.plugins.classparser.ConversationReflectFunction.conversationClickListener
import com.zdy.project.wechat_chatroom_helper.wechat.plugins.classparser.ConversationReflectFunction.conversationWithCacheAdapter
import com.zdy.project.wechat_chatroom_helper.wechat.plugins.classparser.WXObject
import com.zdy.project.wechat_chatroom_helper.wechat.plugins.hook.main.MainLauncherUI
import com.zdy.project.wechat_chatroom_helper.wechat.plugins.hook.message.MessageFactory
import com.zdy.project.wechat_chatroom_helper.wechat.plugins.hook.message.MessageHandler
import com.zdy.project.wechat_chatroom_helper.wechat.plugins.interfaces.MessageEventNotifyListener
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import net.dongliu.apk.parser.Main
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.reflect.ParameterizedType

@SuppressLint("StaticFieldLeak")
/**
 * Created by Mr.Zdy on 2018/4/1.
 */
object MainAdapter {

    lateinit var originAdapter: BaseAdapter
    lateinit var listView: ListView

    var firstChatRoomPosition = -1
    var firstOfficialPosition = -1


    fun isOriginAdapterIsInitialized() = MainAdapter::originAdapter.isInitialized

    fun executeHook() {
        ConversationReflectFunction

        val conversationWithCacheAdapterGetItem = conversationWithCacheAdapter.superclass.declaredMethods
                .filter { it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.java }
                .first { it.name != "getItem" && it.name != "getItemId" }.name

        hookAllConstructors(conversationWithCacheAdapter, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val adapter = param.thisObject as? BaseAdapter ?: return
                originAdapter = adapter
            }
        })



        findAndHookMethod(conversationWithCacheAdapter.superclass, WXObject.Adapter.M.GetCount, object : XC_MethodHook() {

            override fun afterHookedMethod(param: MethodHookParam) {
                var count = param.result as Int + (if (firstChatRoomPosition != -1) 1 else 0)
                count += (if (firstOfficialPosition != -1) 1 else 0)
                param.result = count
            }
        })

        findAndHookMethod(conversationClickListener, WXObject.Adapter.M.OnItemClick,
                AdapterView::class.java, View::class.java, Int::class.java, Long::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {

                        val view = param.result as View?
                        val position = (param.args[2] as Int) - listView.headerViewsCount

                        LogUtils.log("TrackHelperCan'tOpen, MainAdapter -> HookItemClickListener -> onItemClick ")

                        if (position == firstChatRoomPosition) {
                            LogUtils.log("TrackHelperCan'tOpen, MainAdapter -> HookItemClickListener -> onItemClick -> chatRoomClickPerform, RuntimeInfo.chatRoomViewPresenter = ${RuntimeInfo.chatRoomViewPresenter}")
                            RuntimeInfo.chatRoomViewPresenter.show()
                            param.result = null
                        }
                        if (position == firstOfficialPosition) {
                            LogUtils.log("TrackHelperCan'tOpen, MainAdapter -> HookItemClickListener -> onItemClick -> officialClickPerform, RuntimeInfo.officialViewPresenter = ${RuntimeInfo.officialViewPresenter}")
                            RuntimeInfo.officialViewPresenter.show()
                            param.result = null
                        }
                    }
                })

        findAndHookMethod(conversationWithCacheAdapter, WXObject.Adapter.M.GetView,
                Int::class.java, View::class.java, ViewGroup::class.java,
                object : XC_MethodHook() {

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val position = param.args[0] as Int
                        val view = param.result as View?

                        LogUtils.log("MMBaseAdapter_getView, afterHookedMethod, index = $position, view = $view")

                        if (position == firstChatRoomPosition || position == firstOfficialPosition) {

                            if (view != null) {
                                refreshEntryView(view, position, param)
                            }
                        }
                    }


                    private fun refreshEntryView(view: View?, position: Int, param: MethodHookParam) {
                        LogUtils.log("MessageHooker2.6,position = $position, position = $position, " +
                                "firstChatRoomPosition = $firstChatRoomPosition ,firstOfficialPosition = $firstOfficialPosition \n")

                        val itemView = view as ViewGroup

                        val avatarContainer = itemView.getChildAt(0) as ViewGroup
                        val textContainer = itemView.getChildAt(1) as ViewGroup

                        val avatar = avatarContainer.getChildAt(0) as ImageView
                        val unReadCount = avatarContainer.getChildAt(1) as TextView
                        val unMuteReadIndicators = avatarContainer.getChildAt(2) as ImageView

                        val nickname = ((textContainer.getChildAt(0) as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(0)
                        val time = (textContainer.getChildAt(0) as ViewGroup).getChildAt(1)

                        val sendStatus = ((textContainer.getChildAt(1) as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(0)
                        val content = ((textContainer.getChildAt(1) as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(1)
                        val muteImage = ((textContainer.getChildAt(1) as ViewGroup).getChildAt(1) as ViewGroup).getChildAt(1)


                        if (position == firstChatRoomPosition) {
                            unReadCount.visibility = View.GONE
                            unMuteReadIndicators.visibility = View.GONE
                            XposedHelpers.callMethod(content, "setDrawLeftDrawable", false)

                            val allChatRoom = MessageFactory.getSpecChatRoom()
                            val unReadCountItem = MessageFactory.getUnReadCountItem(allChatRoom)
                            val totalUnReadCount = MessageFactory.getUnReadCount(allChatRoom)
                            val unMuteUnReadCount = MessageFactory.getUnMuteUnReadCount(allChatRoom)
                            LogUtils.log("getUnReadCountItemChatRoom " + allChatRoom.joinToString { "unReadCount = ${it.unReadCount}" })

                            val chatInfoModel = allChatRoom.sortedBy { -it.field_conversationTime }.first()

                            setTextForNoMeasuredTextView(nickname, "群聊消息" + (if (MainAdapterLongClick.chatRoomStickyValue > 0) " - 置顶" else ""))
                            setTextForNoMeasuredTextView(time, chatInfoModel.conversationTime)
                            avatar.setImageDrawable(DrawableMaker.handleAvatarDrawable(avatar.context, PageType.CHAT_ROOMS))

                            sendStatus.visibility = View.GONE
                            muteImage.visibility = View.GONE

                            if (unReadCountItem > 0) {

                                val spannableStringBuilder = SpannableStringBuilder()

                                var firstLength = 0
                                if (unMuteUnReadCount > 0) {
                                    spannableStringBuilder.append("[${unMuteUnReadCount}条] ")
                                    firstLength = spannableStringBuilder.length
                                    spannableStringBuilder.setSpan(ForegroundColorSpan(0xFFF44336.toInt()), 0, firstLength, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                                }
                                spannableStringBuilder.append("[ ${unReadCountItem} 个群聊收到 ${totalUnReadCount} 条新消息]")
                                spannableStringBuilder.setSpan(ForegroundColorSpan(0xFFF57C00.toInt()), firstLength, spannableStringBuilder.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)

                                setTextForNoMeasuredTextView(content, spannableStringBuilder)
                                unMuteReadIndicators.visibility = View.VISIBLE
                            } else {
                                setTextColorForNoMeasuredTextView(content, 0xFF999999.toInt())
                                setTextForNoMeasuredTextView(content, "${chatInfoModel.nickname}：${chatInfoModel.content}")
                                unMuteReadIndicators.visibility = View.GONE
                            }


                            if (MainAdapterLongClick.chatRoomStickyValue > 0) {
                                itemView.background = ColorDrawable(Color.rgb(237, 237, 237))
                                // itemView.setBackgroundResource(WXObject.Adapter.F.ConversationItemHighLightSelectorBackGroundInt)
                            } else {
                                itemView.background = ColorDrawable(Color.rgb(255, 255, 255))
                                //  itemView.setBackgroundResource(WXObject.Adapter.F.ConversationItemSelectorBackGroundInt)
                            }

                            param.result = view

                        }
                        if (position == firstOfficialPosition) {
                            unReadCount.visibility = View.GONE
                            unMuteReadIndicators.visibility = View.GONE
                            XposedHelpers.callMethod(content, "setDrawLeftDrawable", false)


                            val allOfficial = MessageFactory.getSpecOfficial()
                            val unReadCountItem = MessageFactory.getUnReadCountItem(allOfficial)
                            val totalUnReadCount = MessageFactory.getUnReadCount(allOfficial)

                            LogUtils.log("getUnReadCountItemChatRoom " + allOfficial.joinToString { "unReadCount = ${it.unReadCount}" })

                            sendStatus.visibility = View.GONE
                            muteImage.visibility = View.GONE

                            val chatInfoModel = allOfficial.sortedBy { -it.field_conversationTime }.first()

                            setTextForNoMeasuredTextView(nickname, "服务号消息" + (if (MainAdapterLongClick.officialStickyValue > 0) " - 置顶" else ""))
                            setTextForNoMeasuredTextView(time, chatInfoModel.conversationTime)
                            avatar.setImageDrawable(DrawableMaker.handleAvatarDrawable(avatar.context, PageType.OFFICIAL))

                            if (unReadCountItem > 0) {
                                setTextForNoMeasuredTextView(content, "[ ${unReadCountItem} 个服务号收到 ${totalUnReadCount} 条新消息]")
                                setTextColorForNoMeasuredTextView(content, 0xFFF57C00.toInt())
                                unMuteReadIndicators.visibility = View.VISIBLE
                            } else {
                                setTextForNoMeasuredTextView(content, "${chatInfoModel.nickname}：${chatInfoModel.content}")
                                setTextColorForNoMeasuredTextView(content, 0xFF999999.toInt())
                                unMuteReadIndicators.visibility = View.GONE
                            }

                            if (MainAdapterLongClick.officialStickyValue > 0) {
                                itemView.background = ColorDrawable(Color.rgb(237, 237, 237))
                                //   itemView.setBackgroundResource(WXObject.Adapter.F.ConversationItemHighLightSelectorBackGroundInt)
                            } else {
                                itemView.background = ColorDrawable(Color.rgb(255, 255, 255))
                                // itemView.setBackgroundResource(WXObject.Adapter.F.ConversationItemSelectorBackGroundInt)
                            }

                            param.result = view
                        }

                    }
                })


        /**
         * 修改 getObject 的数据下标
         */
        findAndHookMethod(conversationWithCacheAdapter.superclass, conversationWithCacheAdapterGetItem,
                Int::class.java, object : XC_MethodHook() {

            private var getItemChatRoomFlag = false
            private var getItemOfficialFlag = false

            override fun beforeHookedMethod(param: MethodHookParam) {

                LogUtils.log("MessageHooker 2019-04-12 15:36:49, thisObject className = ${param.thisObject::class.java.name}, adapter className = ${conversationWithCacheAdapter.name}")

                if (param.thisObject::class.java.name != conversationWithCacheAdapter.name) return

                /**
                 * 附加长按逻辑
                 *
                 * 在 onItemLongClick 内会调用 getItem 方法来获取 bean ，使用 flag 来判断是否有必要拦截
                 */
                if (MainAdapterLongClick.onItemLongClickMethodInvokeGetItemFlagNickName != "") {
                    param.result = getSpecItemForPlaceHolder(MainAdapterLongClick.onItemLongClickMethodInvokeGetItemFlagNickName)
                    MainAdapterLongClick.onItemLongClickMethodInvokeGetItemFlagNickName = ""
                    return
                }
                /**
                 * 长按逻辑结束
                 */

                val index = param.args[0] as Int

                /**
                 * 比较两个入口的先后并确定位置
                 */
                val min = Math.min(firstChatRoomPosition, firstOfficialPosition)
                val max = Math.max(firstChatRoomPosition, firstOfficialPosition)


                LogUtils.log("MessageHooker 2019-04-02 09:18:31, size = ${originAdapter.count}, firstChatRoomPosition = $firstChatRoomPosition, firstOfficialPosition = $firstOfficialPosition")

                val newIndex =
                //如果没有群助手和公众号
                        if (min == -1 && max == -1) {
                            index
                        }
                        //群助手和公众号只有一个
                        else if (min == -1 || max == -1) {
                            when {
                                index < max -> {
                                    index
                                }
                                index == max -> {
                                    handleEntryPosition(index)
                                }
                                index > max -> {
                                    index - 1
                                }
                                else -> {
                                    index
                                }
                            }
                        }
                        //群助手和公众号都存在
                        else {
                            if (index < min) {
                                index
                            } else if (index == min) {
                                handleEntryPosition(index)
                            } else if (index > min && index < max) {
                                index - 1
                            } else if (index == max) {
                                handleEntryPosition(index)
                            } else if (index > max) {
                                index - 2
                            } else {
                                index
                            }
                        }

                LogUtils.log("MessageHook 2019-04-03 15:30:00, size = ${originAdapter.count}, min = $min, max = $max, oldIndex = ${param.args[0]}, newIndex = $newIndex")

                param.args[0] = newIndex
            }

            override fun afterHookedMethod(param: MethodHookParam) {

                if (param.thisObject::class.java.name != conversationWithCacheAdapter.name) return

                var index = param.args[0] as Int

                when {
                    getItemChatRoomFlag -> {
                        getItemChatRoomFlag = false
                        index = firstChatRoomPosition
                    }
                    getItemOfficialFlag -> {
                        getItemOfficialFlag = false
                        index = firstOfficialPosition
                    }
                    else -> {
                        try {
                            val result = param.result

                            //返回了空的数据，此时getcount和getitem已经无法对应 所以直接刷新list
                            if (result == null) {
                                MainLauncherUI.restartMainActivity()
                                return
                            }
                            var field_flag = XposedHelpers.getLongField(result, "field_flag")
                            var field_username = XposedHelpers.getObjectField(result, "field_username")
                            var field_conversationTime = XposedHelpers.getLongField(result, "field_conversationTime")

                            LogUtils.log("MessageHook 2019-04-01 16:25:57, index = $index, flag = $field_flag, username = $field_username, field_conversationTime = $field_conversationTime")
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                        return
                    }
                }
                val result = getCustomItemForEntry(index)

                var field_flag = XposedHelpers.getLongField(result, "field_flag")
                var field_username = XposedHelpers.getObjectField(result, "field_username")
                var field_conversationTime = XposedHelpers.getLongField(result, "field_conversationTime")

                LogUtils.log("MessageHook 2019-04-01 16:25:57, index = $index, flag = $field_flag, username = $field_username, field_conversationTime = $field_conversationTime")

                param.result = result
            }

            private fun handleEntryPosition(index: Int): Int {
                if (firstChatRoomPosition == index) {
                    getItemChatRoomFlag = true
                } else if (firstOfficialPosition == index) {
                    getItemOfficialFlag = true
                }
                return 0
            }


            private fun getCustomItemForEntry(currentPosition: Int): Any {

                val field_conversationTime: Long
                val field_flag: Long

                val item: Any

                when {
                    firstChatRoomPosition == currentPosition -> {
                        if (MainAdapterLongClick.chatRoomStickyValue > 0) {
                            field_conversationTime = -1L
                            field_flag = System.currentTimeMillis() + (1L shl 62)
                        } else {
                            field_conversationTime = MessageFactory.getSpecChatRoom().first().field_conversationTime
                            field_flag = field_conversationTime
                        }
                        item = getSpecItemForPlaceHolder("chatRoomItem")

                    }
                    firstOfficialPosition == currentPosition -> {

                        if (MainAdapterLongClick.officialStickyValue > 0) {
                            field_conversationTime = -1L
                            field_flag = System.currentTimeMillis() + (1L shl 62)
                        } else {
                            field_conversationTime = MessageFactory.getSpecOfficial().first().field_conversationTime
                            field_flag = field_conversationTime
                        }

                        item = getSpecItemForPlaceHolder("officialItem")
                    }
                    else -> throw RuntimeException("wrong position currentPosition = $currentPosition, firstChatRoomPosition = $firstChatRoomPosition, firstOfficialPosition = $firstOfficialPosition")
                }

                XposedHelpers.setLongField(item, "field_flag", field_flag)
                XposedHelpers.setLongField(item, "field_conversationTime", field_conversationTime)

                LogUtils.log("MessageHook 2019-04-05 14:11:46, index = $currentPosition, flag = $field_flag, field_conversationTime = $field_conversationTime")

                return item
            }

            fun getSpecItemForPlaceHolder(username: CharSequence): Any {
                val clazz = ConversationReflectFunction.conversationWithCacheAdapter
                val beanClass = (clazz.genericSuperclass as ParameterizedType).actualTypeArguments[1] as Class<*>

                val constructor = beanClass.getConstructor(String::class.java)
                val newInstance = constructor.newInstance(username)

                return newInstance
            }

        })

        findAndHookMethod(conversationWithCacheAdapter.superclass, "getChangeType", object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                if (MainLauncherUI.NOTIFY_MAIN_LAUNCHER_UI_LIST_VIEW_FLAG) {
                    MainLauncherUI.NOTIFY_MAIN_LAUNCHER_UI_LIST_VIEW_FLAG = false
                    param.result = 2
                }
            }
        })

        MessageHandler.addMessageEventNotifyListener(object : MessageEventNotifyListener {

            override fun onNewMessageCreate(talker: String, createTime: Long, content: Any) {
                super.onNewMessageCreate(talker, createTime, content)
            }

            override fun onEntryPositionChanged(chatroomPosition: Int, officialPosition: Int) {
                super.onEntryPositionChanged(chatroomPosition, officialPosition)

                if (firstOfficialPosition != officialPosition || firstChatRoomPosition != chatroomPosition) {
                    firstChatRoomPosition = chatroomPosition
                    firstOfficialPosition = officialPosition
                }

                LogUtils.log("onEntryPositionChanged, firstChatRoomPosition = ${firstChatRoomPosition}, firstOfficialPosition = ${firstOfficialPosition}")

                LogUtils.log("onEntryPositionChanged, chatRoomStickyValue = ${MainAdapterLongClick.chatRoomStickyValue}, firstOfficialPosition = ${MainAdapterLongClick.officialStickyValue}")

                /**
                 * 粘性头部~（置顶）
                 */
                if (MainAdapterLongClick.chatRoomStickyValue > 0 && MainAdapterLongClick.officialStickyValue == 0) {
                    if (firstChatRoomPosition > firstOfficialPosition) {
                        firstOfficialPosition += 1
                    }
                    firstChatRoomPosition = 0
                }
                if (MainAdapterLongClick.chatRoomStickyValue == 0 && MainAdapterLongClick.officialStickyValue > 0) {
                    if (firstOfficialPosition > firstChatRoomPosition) {
                        firstChatRoomPosition += 1
                    }
                    firstOfficialPosition = 0
                }
                if (MainAdapterLongClick.chatRoomStickyValue > 0 && MainAdapterLongClick.officialStickyValue > 0) {

                    if (MainAdapterLongClick.chatRoomStickyValue > MainAdapterLongClick.officialStickyValue) {
                        firstChatRoomPosition = 0
                        firstOfficialPosition = 1
                    }
                    if (MainAdapterLongClick.chatRoomStickyValue < MainAdapterLongClick.officialStickyValue) {
                        firstChatRoomPosition = 1
                        firstOfficialPosition = 0
                    }
                }

                LogUtils.log("onEntryPositionChanged2, firstChatRoomPosition = ${firstChatRoomPosition}, firstOfficialPosition = ${firstOfficialPosition}")
            }

        })
    }

    fun setTextForNoMeasuredTextView(noMeasuredTextView: Any, charSequence: CharSequence) = XposedHelpers.callMethod(noMeasuredTextView, "setText", charSequence)
    fun setTextColorForNoMeasuredTextView(noMeasuredTextView: Any, color: Int) = XposedHelpers.callMethod(noMeasuredTextView, "setTextColor", color)

    fun getTextFromNoMeasuredTextView(noMeasuredTextView: Any): CharSequence {
        val mTextField = XposedHelpers.findField(XposedHelpers.findClass(WXObject.Adapter.C.NoMeasuredTextView, RuntimeInfo.classloader), "mText")
        mTextField.isAccessible = true
        return mTextField.get(noMeasuredTextView) as CharSequence
    }
}