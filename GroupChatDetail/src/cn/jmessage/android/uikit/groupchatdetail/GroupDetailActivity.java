package cn.jmessage.android.uikit.groupchatdetail;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.jpush.im.android.api.JMessageClient;
import cn.jpush.im.android.api.callback.GetGroupInfoCallback;
import cn.jpush.im.android.api.callback.GetUserInfoCallback;
import cn.jpush.im.android.api.content.EventNotificationContent;
import cn.jpush.im.android.api.enums.ContentType;
import cn.jpush.im.android.api.event.MessageEvent;
import cn.jpush.im.android.api.model.Conversation;
import cn.jpush.im.android.api.model.GroupInfo;
import cn.jpush.im.android.api.model.UserInfo;
import cn.jpush.im.api.BasicCallback;

public class GroupDetailActivity extends BaseActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private static final String TAG = "GroupDetailActivity";

    private static final String GROUP_ID = "groupId";
    private static final String DELETE_MODE = "deleteMode";
    private static final String MY_USERNAME = "myUsername";
    private static final int MAX_GRID_ITEM = 40;
    private static final int REQUEST_CODE_ALL_MEMBER = 100;
    private static final int ON_GROUP_EVENT = 101;
    private static final int GET_GROUP_INFO_SUCCESS = 102;

    private ChatDetailView mChatDetailView;
    private Context mContext;
    private ProgressDialog mProgressDialog;
    private Dialog mDialog;
    private Dialog mLoadingDialog;
    private long mGroupId;
    private String mGroupName;
    private GroupMemberGridAdapter mAdapter;
    private List<UserInfo> mMembersList;
    // 当前GridView群成员项数
    private int mCurrentNum;
    private boolean mIsCreator;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(IdHelper.getLayout(this, "jmui_activity_group_detail"));
        mContext = this;
        mChatDetailView = (ChatDetailView) findViewById(IdHelper.getViewID(mContext, "jmui_chat_detail_view"));
        mChatDetailView.initModule();
        mChatDetailView.setListeners(this);
        mChatDetailView.setItemListener(this);
        mChatDetailView.setGroupName(this.getString(IdHelper.getString(mContext, "jmui_chat_detail_title")));

        Log.d(TAG, "GroupDetailActivity onCreated!");
        //默认获取到的群信息,可以调用JMessageClient.createGroup或者使用Rest API创建
        mGroupId = getIntent().getLongExtra(GROUP_ID, 0);
        final String myName = getIntent().getStringExtra(MY_USERNAME);
        Conversation conv = JMessageClient.getGroupConversation(mGroupId);
        GroupInfo groupInfo = (GroupInfo) conv.getTargetInfo();
        if (groupInfo != null) {
            mMembersList = groupInfo.getGroupMembers();
            String owner = groupInfo.getGroupOwner();
            if (myName.equals(owner)) {
                mIsCreator = true;
            }
            mGroupName = groupInfo.getGroupName();
            if (!TextUtils.isEmpty(mGroupName)) {
                mChatDetailView.setGroupName(mGroupName);
            }
            initAdapter();
        } else {
            JMessageClient.getGroupInfo(mGroupId, new GetGroupInfoCallback() {
                @Override
                public void gotResult(int status, String desc, GroupInfo groupInfo) {
                    if (status == 0) {
                        mMembersList = groupInfo.getGroupMembers();
                        String owner = groupInfo.getGroupOwner();
                        if (myName.equals(owner)) {
                            mIsCreator = true;
                        }
                        mGroupName = groupInfo.getGroupName();
                        if (!TextUtils.isEmpty(mGroupName)) {
                            mChatDetailView.setGroupName(mGroupName);
                        }
                        mHandler.sendEmptyMessage(GET_GROUP_INFO_SUCCESS);
                    } else {
                        HandleResponseCode.onHandle(mContext, status, false);
                    }
                }
            });
        }
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent();
        if (v.getId() == IdHelper.getViewID(mContext, "jmui_return_btn")) {
            finish();
            //显示所有群成员
        } else if (v.getId() == IdHelper.getViewID(mContext, "jmui_all_member_ll")) {
            intent.putExtra(GROUP_ID, mGroupId);
            intent.putExtra(DELETE_MODE, false);
            intent.setClass(mContext, MembersInChatActivity.class);
            startActivityForResult(intent, REQUEST_CODE_ALL_MEMBER);
            // 设置群组名称
        } else if (v.getId() == IdHelper.getViewID(mContext, "jmui_group_name_ll")) {
            showGroupNameSettingDialog(mGroupId, mGroupName);
        } else if (v.getId() == IdHelper.getViewID(mContext, "jmui_chat_detail_del_group")) {
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (view.getId() == IdHelper.getViewID(mContext, "jmui_cancel_btn")) {
                        mDialog.cancel();
                    } else {
                        //TODO exit group
                        mDialog.cancel();
                    }
                }
            };
            mDialog = DialogCreator.createExitGroupDialog(mContext, listener);
            mDialog.show();
        }
    }

    //设置群聊名称
    public void showGroupNameSettingDialog(final long groupID, String groupName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(IdHelper.getLayout(mContext, "jmui_dialog_reset_password"), null);
        builder.setView(view);
        TextView title = (TextView) view.findViewById(IdHelper.getViewID(mContext, "jmui_title_tv"));
        title.setText(mContext.getString(IdHelper.getString(mContext, "jmui_group_name_hit")));
        final EditText pwdEt = (EditText) view.findViewById(IdHelper.getViewID(mContext, "jmui_password_et"));
        pwdEt.addTextChangedListener(new TextWatcher() {
            private CharSequence temp = "";
            private int editStart;
            private int editEnd;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                temp = s;
            }

            @Override
            public void afterTextChanged(Editable s) {
                editStart = pwdEt.getSelectionStart();
                editEnd = pwdEt.getSelectionEnd();
                byte[] data = temp.toString().getBytes();
                if (data.length > 64) {
                    s.delete(editStart - 1, editEnd);
                    int tempSelection = editStart;
                    pwdEt.setText(s);
                    pwdEt.setSelection(tempSelection);
                }
            }
        });
        pwdEt.setInputType(InputType.TYPE_CLASS_TEXT);
        pwdEt.setHint(groupName);
        pwdEt.setHintTextColor(IdHelper.getColor(mContext, "gray"));
        final Button cancel = (Button) view.findViewById(IdHelper.getViewID(mContext, "jmui_cancel_btn"));
        final Button commit = (Button) view.findViewById(IdHelper.getViewID(mContext, "jmui_commit_btn"));
        final Dialog dialog = builder.create();
        dialog.show();
        dialog.getWindow().setLayout((int) (0.8 * mWidth), WindowManager.LayoutParams.WRAP_CONTENT);
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (view.getId() == IdHelper.getViewID(mContext, "jmui_cancel_btn")) {
                    dialog.cancel();
                } else {
                    final String newName = pwdEt.getText().toString().trim();
                    if (newName.equals("")) {
                        Toast.makeText(mContext, mContext.getString(IdHelper.getString(mContext, "jmui_group_name_not_null_toast")),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        dismissSoftInput();
                        dialog.dismiss();
                        mProgressDialog = new ProgressDialog(mContext);
                        mProgressDialog.setMessage(mContext.getString(IdHelper.getString(mContext, "jmui_modifying_hint")));
                        mProgressDialog.show();
                        JMessageClient.updateGroupName(groupID, newName, new BasicCallback() {
                            @Override
                            public void gotResult(final int status, final String desc) {
                                mProgressDialog.dismiss();
                                if (status == 0) {
                                    mChatDetailView.updateGroupName(newName);
                                    Toast.makeText(mContext, mContext.getString(IdHelper.getString(mContext, "jmui_modify_success_toast")),
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    Log.i(TAG, "desc :" + desc);
                                    HandleResponseCode.onHandle(mContext, status, false);
                                }
                            }
                        });
                    }
                }
            }
        };
        cancel.setOnClickListener(listener);
        commit.setOnClickListener(listener);

    }

    private void dismissSoftInput() {
        //隐藏软键盘
        InputMethodManager imm = ((InputMethodManager) mContext
                .getSystemService(INPUT_METHOD_SERVICE));
        if (this.getWindow().getAttributes().softInputMode != WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN) {
            if (this.getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
            }
        }
    }

    @Override
    public void handleMsg(Message msg) {
        switch (msg.what) {
            case ON_GROUP_EVENT:
//                mAdapter.refreshMemberList(mGroupId);
                refreshMemberList();
                break;
            case GET_GROUP_INFO_SUCCESS:
                initAdapter();
                break;
        }
    }

    private void initAdapter() {
        mAdapter = new GroupMemberGridAdapter(mContext, mMembersList, mIsCreator, mAvatarSize);
        if (mMembersList.size() > 40) {
            mCurrentNum = 39;
        } else {
            mCurrentNum = mMembersList.size();
        }
        mChatDetailView.setAdapter(mAdapter);
        mChatDetailView.setMembersNum(mMembersList.size());
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent intent = new Intent();
        // 点击添加按钮
         if (position == mCurrentNum) {
            addMemberToGroup();

            // 是群主, 成员个数大于1并点击删除按钮
        } else if (position == mCurrentNum + 1 && mIsCreator && mCurrentNum > 1) {
            intent.putExtra(DELETE_MODE, true);
            intent.putExtra(GROUP_ID, mGroupId);
            intent.setClass(mContext, MembersInChatActivity.class);
            startActivityForResult(intent, REQUEST_CODE_ALL_MEMBER);
        }
    }

    //点击添加按钮触发事件
    private void addMemberToGroup() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final View view = LayoutInflater.from(mContext)
                .inflate(IdHelper.getLayout(mContext, "jmui_dialog_add_friend_to_conv_list"), null);
        builder.setView(view);
        TextView title = (TextView) view.findViewById(IdHelper.getViewID(mContext, "jmui_dialog_name"));
        title.setText(mContext.getString(IdHelper.getString(mContext, "jmui_add_friend_to_group_title")));
        final EditText userNameEt = (EditText) view.findViewById(IdHelper.getViewID(mContext, "jmui_user_name_et"));
        final Button cancel = (Button) view.findViewById(IdHelper.getViewID(mContext, "jmui_cancel_btn"));
        final Button commit = (Button) view.findViewById(IdHelper.getViewID(mContext, "jmui_commit_btn"));
        final Dialog dialog = builder.create();
        dialog.show();
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (view.getId() == IdHelper.getViewID(mContext, "jmui_cancel_btn")) {
                    final String targetId = userNameEt.getText().toString().trim();
                    Log.i(TAG, "targetID " + targetId);
                    if (TextUtils.isEmpty(targetId)) {
                        Toast.makeText(mContext, mContext.getString(IdHelper.getString(mContext, "jmui_username_not_null_toast")),
                                Toast.LENGTH_SHORT).show();
                        //检查群组中是否包含该用户
                    } else if (checkIfNotContainUser(targetId)) {
                        mLoadingDialog = DialogCreator.createLoadingDialog(mContext,
                                mContext.getString(IdHelper.getString(mContext, "jmui_searching_user")));
                        mLoadingDialog.show();
                        getUserInfo(targetId, dialog);
                    } else {
                        dialog.cancel();
                        Toast.makeText(mContext, mContext.getString(IdHelper.getString(mContext, "jmui_user_already_exist_toast")),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
        cancel.setOnClickListener(listener);
        commit.setOnClickListener(listener);
    }

    /**
     * 添加成员时检查是否存在该群成员
     *
     * @param targetID 要添加的用户
     * @return 返回是否存在该用户
     */
    private boolean checkIfNotContainUser(String targetID) {
        if (mMembersList != null) {
            for (UserInfo userInfo : mMembersList) {
                if (userInfo.getUserName().equals(targetID))
                    return false;
            }
            return true;
        }
        return true;
    }

    private void getUserInfo(final String targetId, final Dialog dialog){
        JMessageClient.getUserInfo(targetId, new GetUserInfoCallback() {
            @Override
            public void gotResult(final int status, String desc, final UserInfo userInfo) {
                if (mLoadingDialog != null) {
                    mLoadingDialog.dismiss();
                }
                if (status == 0) {
                    addAMember(userInfo);
                    dialog.cancel();
                } else {
                    HandleResponseCode.onHandle(mContext, status, true);
                }
            }
        });
    }

    /**
     * @param userInfo 要增加的成员的用户名，目前一次只能增加一个
     */
    private void addAMember(final UserInfo userInfo) {
        mLoadingDialog = DialogCreator.createLoadingDialog(mContext,
                mContext.getString(IdHelper.getString(mContext, "jmui_adding_hint")));
        mLoadingDialog.show();
        ArrayList<String> list = new ArrayList<>();
        list.add(userInfo.getUserName());
        JMessageClient.addGroupMembers(mGroupId, list, new BasicCallback() {

            @Override
            public void gotResult(final int status, final String desc) {
                if (status == 0) {
//                    mAdapter.refreshMemberList(mGroupId);
                    refreshMemberList();
                    mCurrentNum++;
                    mChatDetailView.setTitle(mMembersList.size() + 1);
                    mChatDetailView.setMembersNum(mMembersList.size() + 1);
                    mLoadingDialog.dismiss();
                } else {
                    mLoadingDialog.dismiss();
                    HandleResponseCode.onHandle(mContext, status, true);
                }
            }
        });
    }

    /**
     * 接收群成员变化事件
     *
     * @param event 消息事件
     */
    public void onEvent(MessageEvent event) {
        final cn.jpush.im.android.api.model.Message msg = event.getMessage();
        if (msg.getContentType() == ContentType.eventNotification) {
            EventNotificationContent.EventNotificationType msgType = ((EventNotificationContent) msg
                    .getContent()).getEventNotificationType();
            switch (msgType) {
                //添加群成员事件特殊处理
                case group_member_added:
                    List<String> userNames = ((EventNotificationContent) msg.getContent()).getUserNames();
                    for (final String userName : userNames) {
                        JMessageClient.getUserInfo(userName, new GetUserInfoCallback() {
                            @Override
                            public void gotResult(int status, String desc, UserInfo userInfo) {
                                if (status == 0) {
                                    mAdapter.notifyDataSetChanged();
                                } else {
                                    HandleResponseCode.onHandle(mContext, status, false);
                                }
                            }
                        });
                    }
                    break;
                case group_member_removed:
                    break;
                case group_member_exit:
                    break;
            }
            //无论是否添加群成员，刷新界面
            mHandler.sendEmptyMessage(ON_GROUP_EVENT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ALL_MEMBER) {
            refreshMemberList();
        }
    }

    private void refreshMemberList() {
        Conversation conv = JMessageClient.getGroupConversation(mGroupId);
        GroupInfo groupInfo = (GroupInfo)conv.getTargetInfo();
        mMembersList = groupInfo.getGroupMembers();
        mCurrentNum = mMembersList.size() > MAX_GRID_ITEM ? MAX_GRID_ITEM - 1 : mMembersList.size();
        mAdapter.refreshMemberList(mMembersList);
        mChatDetailView.setMembersNum(mMembersList.size());
        mChatDetailView.setTitle(mMembersList.size());
    }

}
