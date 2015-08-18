package com.quickblox.q_municate.ui.friends;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.quickblox.chat.model.QBDialog;
import com.quickblox.q_municate.R;
import com.quickblox.q_municate.ui.base.BaseLogeableActivity;
import com.quickblox.q_municate.ui.chats.privatedialog.PrivateDialogActivity;
import com.quickblox.q_municate.ui.dialogs.AlertDialog;
import com.quickblox.q_municate.ui.mediacall.CallActivity;
import com.quickblox.q_municate.ui.views.RoundedImageView;
import com.quickblox.q_municate.utils.ImageLoaderUtils;
import com.quickblox.q_municate_core.core.command.Command;
import com.quickblox.q_municate_core.models.AppSession;
import com.quickblox.q_municate_core.qb.commands.QBCreatePrivateChatCommand;
import com.quickblox.q_municate_core.qb.commands.QBDeleteChatCommand;
import com.quickblox.q_municate_core.qb.commands.QBRemoveFriendCommand;
import com.quickblox.q_municate_core.service.QBService;
import com.quickblox.q_municate_core.service.QBServiceConsts;
import com.quickblox.q_municate_core.utils.ChatUtils;
import com.quickblox.q_municate_core.utils.DialogUtils;
import com.quickblox.q_municate_core.utils.OnlineStatusHelper;
import com.quickblox.q_municate_db.managers.DataManager;
import com.quickblox.q_municate_db.managers.UserDataManager;
import com.quickblox.q_municate_db.models.Dialog;
import com.quickblox.q_municate_db.models.DialogOccupant;
import com.quickblox.q_municate_db.models.User;

import java.util.Observable;
import java.util.Observer;

public class FriendDetailsActivity extends BaseLogeableActivity {

    private RoundedImageView avatarImageView;
    private TextView nameTextView;
    private TextView statusTextView;
    private ImageView onlineImageView;
    private TextView onlineStatusTextView;
    private TextView phoneTextView;
    private View phoneView;

    private DataManager dataManager;
    private int userId;
    private User user;
    private Observer userObserver;

    public static void start(Context context, int friendId) {
        Intent intent = new Intent(context, FriendDetailsActivity.class);
        intent.putExtra(QBServiceConsts.EXTRA_FRIEND_ID, friendId);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_details);

        initFields();
        initUI();
        initUIWithUsersData();
        addActions();
    }

    private void initFields() {
        dataManager = DataManager.getInstance();
        canPerformLogout.set(true);
        userId = getIntent().getExtras().getInt(QBServiceConsts.EXTRA_FRIEND_ID);
        user = dataManager.getUserDataManager().get(userId);
        userObserver = new UserObserver();
    }

    private void initUI() {
        avatarImageView = _findViewById(R.id.avatar_imageview);
        nameTextView = _findViewById(R.id.name_textview);
        statusTextView = _findViewById(R.id.status_textview);
        onlineImageView = _findViewById(R.id.online_imageview);
        onlineStatusTextView = _findViewById(R.id.online_status_textview);
        phoneTextView = _findViewById(R.id.phone_textview);
        phoneView = _findViewById(R.id.phone_relativelayout);
    }

    @Override
    public void onConnectedToService(QBService service) {
        super.onConnectedToService(service);

        if (friendListHelper != null) {
            setOnlineStatus(user);
        }
    }

    private void addActions() {
        addAction(QBServiceConsts.REMOVE_FRIEND_SUCCESS_ACTION, new RemoveFriendSuccessAction());
        addAction(QBServiceConsts.REMOVE_FRIEND_FAIL_ACTION, failAction);

        addAction(QBServiceConsts.CREATE_PRIVATE_CHAT_SUCCESS_ACTION, new CreatePrivateChatSuccessAction());
        addAction(QBServiceConsts.CREATE_PRIVATE_CHAT_FAIL_ACTION, failAction);
    }

    private void initUIWithUsersData() {
        loadAvatar();
        setName();
        setOnlineStatus(user);
        setStatus();
        setPhone();
    }

    private void setStatus() {
        if (!TextUtils.isEmpty(user.getStatus())) {
            statusTextView.setText(user.getStatus());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        addObservers();

        setOnlineStatus(user);
    }

    @Override
    protected void onPause() {
        super.onPause();
        deleteObservers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeActions();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.friend_details_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_delete:
                showRemoveUserDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void removeActions() {
        removeAction(QBServiceConsts.REMOVE_FRIEND_SUCCESS_ACTION);
        removeAction(QBServiceConsts.REMOVE_FRIEND_FAIL_ACTION);

        removeAction(QBServiceConsts.CREATE_PRIVATE_CHAT_SUCCESS_ACTION);
        removeAction(QBServiceConsts.CREATE_PRIVATE_CHAT_FAIL_ACTION);
    }

    private void addObservers() {
        dataManager.getUserDataManager().addObserver(userObserver);
    }

    private void deleteObservers() {
        dataManager.getUserDataManager().deleteObserver(userObserver);
    }

    private void setName() {
        nameTextView.setText(user.getFullName());
    }

    private void setPhone() {
        if (user.getPhone() != null) {
            phoneView.setVisibility(View.VISIBLE);
        } else {
            phoneView.setVisibility(View.GONE);
        }
        phoneTextView.setText(user.getPhone());
    }

    private void setOnlineStatus(User user) {
        if (user != null && friendListHelper != null) {
            boolean online = friendListHelper.isUserOnline(user.getUserId());

            if (online) {
                onlineImageView.setVisibility(View.VISIBLE);
            } else {
                onlineImageView.setVisibility(View.GONE);
            }

            onlineStatusTextView.setText(OnlineStatusHelper.getOnlineStatus(online));
        }
    }

    private void loadAvatar() {
        String url = user.getAvatar();
        ImageLoader.getInstance().displayImage(url, avatarImageView, ImageLoaderUtils.UIL_USER_AVATAR_DISPLAY_OPTIONS);
    }

    private void showRemoveUserDialog() {
        AlertDialog alertDialog = AlertDialog.newInstance(getResources().getString(
                R.string.frd_dlg_remove_friend, user.getFullName()));
        alertDialog.setPositiveButton(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showProgress();
                QBRemoveFriendCommand.start(FriendDetailsActivity.this, user.getUserId());
            }
        });
        alertDialog.setNegativeButton(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        alertDialog.show(getFragmentManager(), null);
    }

    public void videoCallClickListener(View view) {
        callToUser(user, com.quickblox.videochat.webrtc.Consts.MEDIA_STREAM.VIDEO);
    }

    private void callToUser(User friend, com.quickblox.videochat.webrtc.Consts.MEDIA_STREAM callType) {
        if (friend.getUserId() != AppSession.getSession().getUser().getId()) {
            if (checkFriendStatus(friend.getUserId())) {
                CallActivity.start(FriendDetailsActivity.this, friend, callType);
            }
        }
    }

    public void voiceCallClickListener(View view) {
        callToUser(user, com.quickblox.videochat.webrtc.Consts.MEDIA_STREAM.AUDIO);
    }

    private boolean checkFriendStatus(int userId) {
        boolean isFriend = DataManager.getInstance().getFriendDataManager().getByUserId(userId) != null;
        if (isFriend) {
            return true;
        } else {
            DialogUtils.showLong(this, getResources().getString(R.string.dlg_user_is_not_friend));
            return false;
        }
    }

    public void chatClickListener(View view) {
        if (checkFriendStatus(user.getUserId())) {
            QBCreatePrivateChatCommand.start(this, user);
        }
    }

    private void deleteDialog() {
        DialogOccupant dialogOccupant = dataManager.getDialogOccupantDataManager().getDialogOccupantForPrivateChat(user.getUserId());
        String dialogId = dialogOccupant.getDialog().getDialogId();
        QBDeleteChatCommand.start(this, dialogId, Dialog.Type.PRIVATE);
    }

    @Override
    public void onChangedUserStatus(int userId, boolean online) {
        super.onChangedUserStatus(userId, online);
        setOnlineStatus(user);
    }

    private class UserObserver implements Observer {

        @Override
        public void update(Observable observable, Object data) {
            if (data != null && data.equals(UserDataManager.OBSERVE_KEY)) {
                user = DataManager.getInstance().getUserDataManager().get(userId);
                initUIWithUsersData();
            }
        }
    }

    private class RemoveFriendSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            deleteDialog();
            DialogUtils.showLong(FriendDetailsActivity.this,
                    getString(R.string.dlg_friend_removed, user.getFullName()));
            finish();
        }
    }

    private class CreatePrivateChatSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) throws Exception {
            QBDialog qbDialog = (QBDialog) bundle.getSerializable(QBServiceConsts.EXTRA_DIALOG);
            PrivateDialogActivity.start(FriendDetailsActivity.this, user, ChatUtils.createLocalDialog(qbDialog));
        }
    }
}