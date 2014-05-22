package com.quickblox.qmunicate.qb.helpers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import android.util.Log;
import com.quickblox.internal.core.exception.QBResponseException;
import com.quickblox.module.chat.QBChatMessage;
import com.quickblox.module.chat.QBChatService;
import com.quickblox.module.chat.QBPrivateChat;
import com.quickblox.module.chat.QBPrivateChatManager;
import com.quickblox.module.chat.QBRoomChat;
import com.quickblox.module.chat.QBRoomChatManager;
import com.quickblox.module.chat.listeners.QBMessageListener;
import com.quickblox.module.chat.listeners.QBPrivateChatManagerListener;
import com.quickblox.module.chat.listeners.QBRoomChatManagerListener;
import com.quickblox.module.chat.model.QBAttachment;
import com.quickblox.module.content.QBContent;
import com.quickblox.module.content.model.QBFile;
import com.quickblox.module.users.model.QBUser;
import com.quickblox.qmunicate.App;
import com.quickblox.qmunicate.R;
import com.quickblox.qmunicate.caching.DatabaseManager;
import com.quickblox.qmunicate.model.PrivateChatMessageCache;
import com.quickblox.qmunicate.service.QBServiceConsts;
import com.quickblox.qmunicate.utils.Consts;
import com.quickblox.qmunicate.utils.ErrorUtils;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class QBChatHelper extends BaseHelper implements QBMessageListener<QBPrivateChat>, QBPrivateChatManagerListener, QBRoomChatManagerListener {

    private QBRoomChat roomChat;
    private QBRoomChatManager roomChatManager;
    private QBUser user;
    private QBChatService chatService;
    private QBPrivateChat privateChat;
    private QBPrivateChatManager privateChatManager;
    private int privateChatId;
    private String groupChatName;
    private String opponentName;
    private String membersIDs = "";

    public QBChatHelper(Context context) {
        super(context);
    }

    public void sendPrivateMessage(String message) {
        QBChatMessage chatMessage = getQBChatMessage(message);
        try {
            privateChat.sendMessage(chatMessage);
        } catch (XMPPException e) {
            ErrorUtils.showError(context, e);
        } catch (SmackException.NotConnectedException e) {
            ErrorUtils.showError(context, e);
        }
        saveMessageToCache(new PrivateChatMessageCache(chatMessage.getBody(), user.getId(), privateChatId, Consts.EMPTY_STRING, opponentName));
    }

    private QBChatMessage getQBChatMessage(String body) {
        QBChatMessage chatMessage = new QBChatMessage();
        chatMessage.setBody(body);
        return chatMessage;
    }

    public void saveMessageToCache(PrivateChatMessageCache privateChatMessageCache) {
        DatabaseManager.savePrivateChatMessage(context, privateChatMessageCache);
    }

    public void sendGroupMessage(String message) {
        QBChatMessage chatMessage = getQBChatMessage(message);
        try {
            roomChat.sendMessage(chatMessage);
        } catch (XMPPException e) {
            ErrorUtils.showError(context, e);
        } catch (SmackException.NotConnectedException e) {
            ErrorUtils.showError(context, e);
            //TODO: SS reconnect
        }
        Log.i("GroupMessage: ", " Chat ID: " + groupChatName);

        saveGroupMessageToCache(chatMessage, user.getId(), groupChatName, membersIDs);
    }

    public void sendPrivateMessageWithAttachImage(QBFile qbFile) {
        QBChatMessage chatMessage = getQBChatMessageWithImage(qbFile);
        try {
            privateChat.sendMessage(chatMessage);
        } catch (XMPPException e) {
            ErrorUtils.showError(context, e);
        } catch (SmackException.NotConnectedException e) {
            ErrorUtils.showError(context, e);
        }
        saveMessageToCache(new PrivateChatMessageCache(Consts.EMPTY_STRING, user.getId(), privateChatId, qbFile.getPublicUrl(), opponentName));
    }


    private void saveGroupMessageToCache(QBChatMessage chatMessage, int senderId, String groupId, String membersIds){
        Log.i("GroupMessage: ", " Saving to cache " + groupChatName);
        DatabaseManager.saveGroupChatMessage(context, chatMessage, senderId, groupId, membersIds);
    }

    private QBChatMessage getQBChatMessageWithImage(QBFile qbFile) {
        QBChatMessage chatMessage = new QBChatMessage();
        QBAttachment attachment = new QBAttachment(QBAttachment.PHOTO_TYPE);
        attachment.setUrl(qbFile.getPublicUrl());
        chatMessage.addAttachment(attachment);
        return chatMessage;
    }

    @Override
    public void processMessage(QBPrivateChat privateChat, QBChatMessage chatMessage) {
        Intent intent = new Intent(QBServiceConsts.GOT_CHAT_MESSAGE);
        String messageBody = getMessageBody(chatMessage);
        String extraChatMessage = "";
        if(TextUtils.isEmpty(messageBody)){
            extraChatMessage = context.getResources().getString(R.string.file_was_attached);
        } else {
            extraChatMessage = messageBody;
        }
        intent.putExtra(QBServiceConsts.EXTRA_CHAT_MESSAGE, extraChatMessage);
        intent.putExtra(QBServiceConsts.EXTRA_SENDER_CHAT_MESSAGE, DatabaseManager.getFriend(context,
                chatMessage.getSenderId()).getFullname());
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        String attachURL = "";
        if(TextUtils.isEmpty(messageBody)){
            attachURL = getAttachUrlFromQBChatMessage(chatMessage);
        } else {
            attachURL = Consts.EMPTY_STRING;
        }

        saveMessageToCache(new PrivateChatMessageCache(messageBody, chatMessage.getSenderId(), chatMessage.getSenderId(),
                attachURL, opponentName));
    }

    private String getMessageBody(QBChatMessage chatMessage) {
        String messageBody = chatMessage.getBody();
        if (TextUtils.isEmpty(messageBody)) {
            messageBody = Consts.EMPTY_STRING;
        }
        return messageBody;
    }

    private String getAttachUrlFromQBChatMessage(QBChatMessage chatMessage) {
        List<QBAttachment> attachmentsList = new ArrayList<QBAttachment>(chatMessage.getAttachments());
        if (!attachmentsList.isEmpty()) {
            return attachmentsList.get(attachmentsList.size() - 1).getUrl();
        }
        return Consts.EMPTY_STRING;
    }

    @Override
    public void chatCreated(QBPrivateChat privateChat, boolean createdLocally) {
        privateChat.addMessageListener(this);
    }

    @Override
    public void roomCreated(QBRoomChat qbRoomChat) {

    }

    @Override
    public void roomJoined(QBRoomChat qbRoomChat) {

    }

    @Override
    public void onError(List<String> strings) {

    }

    public void init() {
        chatService = QBChatService.getInstance();
        privateChatManager = chatService.getPrivateChatManager();
        privateChatManager.addPrivateChatManagerListener(this);
        roomChatManager = chatService.getRoomChatManager();
        roomChatManager.addRoomChatManagerListener(this);
    }

    public void initPrivateChat(int opponentId, String opponentName) {
        user = App.getInstance().getUser();
        privateChat = privateChatManager.createChat(opponentId, this);
        privateChatId = opponentId;
        this.opponentName = opponentName;
    }

    public void initRoomChat(Context context, String roomName, List<Friend> friendList) {
        this.context = context;
        user = App.getInstance().getUser();
        roomChat = roomChatManager.createRoom(roomName);
        try {
            roomChat.join();
            roomChat.addRoomUser(user.getId());
            for (Friend friend : friendList) {
                roomChat.addRoomUser(Integer.valueOf(friend.getId()));
                if(friend != null){
                    membersIDs = membersIDs + friend.getId() + ",";
                }
            }
            Log.i("Members IDs", membersIDs);
        } catch (Exception e) {
            ErrorUtils.showError(context, e);
        }
        groupChatName = roomName;
    }

    public QBFile loadAttachFile(File file) {
        QBFile qbFile = null;
        try {
            qbFile = QBContent.uploadFileTask(file, true, (String) null);
        } catch (QBResponseException e) {
            ErrorUtils.showError(context, e);
        }
        return qbFile;
    }

    public void login(QBUser user) {
        try {
            QBChatService.init(context);
            if (!QBChatService.getInstance().isLoggedIn()) {
                QBChatService.getInstance().login(user);
                this.user = user;
            }
        } catch (Exception e) {
            ErrorUtils.logError(e);
        }
    }

    public void logout() throws QBResponseException {
        try {
            QBChatService.getInstance().logout();
        } catch (SmackException.NotConnectedException e) {
            throw new QBResponseException(e.getMessage());
        }
    }

    public void destroy() {
        QBChatService.getInstance().destroy();
    }
}