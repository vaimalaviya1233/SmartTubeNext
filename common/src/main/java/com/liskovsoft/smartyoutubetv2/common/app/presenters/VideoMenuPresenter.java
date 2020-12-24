package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.VideoPlaylistInfo;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class VideoMenuPresenter extends BasePresenter<Void> {
    private final MediaItemManager mItemManager;
    private final SignInManager mAuthManager;
    private final AppSettingsPresenter mSettingsPresenter;
    private Disposable mPlaylistAction;
    private Disposable mAddAction;
    private Disposable mSignCheckAction;
    private Disposable mNotInterestedAction;
    private Disposable mSubscribeAction;
    private Video mVideo;
    private boolean mIsNotInterestedButtonEnabled;
    private boolean mIsOpenChannelButtonEnabled;
    private boolean mIsOpenChannelUploadsButtonEnabled;
    private boolean mIsSubscribeButtonEnabled;
    private boolean mIsShareButtonEnabled;

    private VideoMenuPresenter(Context context) {
        super(context);
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mAuthManager = service.getSignInManager();
        mSettingsPresenter = AppSettingsPresenter.instance(context);
    }

    public static VideoMenuPresenter instance(Context context) {
        return new VideoMenuPresenter(context);
    }

    public void showShortMenu(Video video) {
        showMenuInt(video);
    }

    public void showMenu(Video video) {
        mIsOpenChannelButtonEnabled = true;
        mIsOpenChannelUploadsButtonEnabled = true;
        mIsNotInterestedButtonEnabled = true;
        mIsShareButtonEnabled = true;
        mIsSubscribeButtonEnabled = true;

        showMenuInt(video);
    }

    private void showMenuInt(Video video) {
        if (video == null || !video.isVideo()) {
            return;
        }

        mVideo = video;

        authCheck(this::obtainPlaylistsAndShow,
                  () -> MessageHelpers.showMessage(getContext(), R.string.msg_signed_users_only));;
    }

    private void obtainPlaylistsAndShow() {
        mPlaylistAction = mItemManager.getVideoPlaylistsInfosObserve(mVideo.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::prepareAndShowDialog);
    }

    private void prepareAndShowDialog(List<VideoPlaylistInfo> videoPlaylistInfos) {
        mSettingsPresenter.clear();

        appendAddToPlaylist(videoPlaylistInfos);
        appendOpenChannelButton();
        //appendOpenChannelUploadsButton();
        appendSubscribeButton();
        appendNotInterestedButton();
        appendShareButton();

        mSettingsPresenter.showDialog(mVideo.title, () -> RxUtils.disposeActions(mPlaylistAction, mAddAction, mSignCheckAction, mNotInterestedAction, mSubscribeAction));
    }

    private void appendAddToPlaylist(List<VideoPlaylistInfo> videoPlaylistInfos) {
        List<OptionItem> options = new ArrayList<>();

        for (VideoPlaylistInfo playlistInfo : videoPlaylistInfos) {
            options.add(UiOptionItem.from(
                    playlistInfo.getTitle(),
                    (item) -> this.addToPlaylist(playlistInfo.getPlaylistId(), item.isSelected()),
                    playlistInfo.isSelected()));
        }

        mSettingsPresenter.appendCheckedCategory(getContext().getString(R.string.dialog_add_to_playlist), options);
    }

    private void appendOpenChannelButton() {
        if (!mIsOpenChannelButtonEnabled || mVideo == null || mVideo.channelId == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel), optionItem -> ChannelPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendOpenChannelUploadsButton() {
        if (!mIsOpenChannelUploadsButtonEnabled || mVideo == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel_uploads), optionItem -> ChannelUploadsPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendNotInterestedButton() {
        if (!mIsNotInterestedButtonEnabled || mVideo == null || mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken() == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.not_interested), optionItem -> {
                    mNotInterestedAction = mItemManager.markAsNotInterestedObserve(mVideo.mediaItem)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((var) -> {}, (err) -> {}, () -> {
                                MessageHelpers.showMessage(getContext(), R.string.you_wont_see_this_video);
                            });
                }));
    }

    private void appendShareButton() {
        if (!mIsShareButtonEnabled || mVideo == null || mVideo.videoId == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.send_to), optionItem -> {
                    Utils.displayShareVideoDialog(getContext(), mVideo.videoId);
                }));
    }

    private void appendSubscribeButton() {
        if (!mIsSubscribeButtonEnabled || mVideo == null || mVideo.channelId == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        mVideo.subscribed ? R.string.unsubscribe_from_channel : R.string.subscribe_to_channel),
                        optionItem -> subscribe(mVideo.channelId, mVideo.subscribed)));
    }

    private void addToPlaylist(String playlistId, boolean checked) {
        RxUtils.disposeActions(mPlaylistAction, mAddAction, mSignCheckAction);
        Observable<Void> editObserve;

        if (checked) {
            editObserve = mItemManager.addToPlaylistObserve(playlistId, mVideo.videoId);
        } else {
            editObserve = mItemManager.removeFromPlaylistObserve(playlistId, mVideo.videoId);
        }

        mAddAction = editObserve
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    private void authCheck(Runnable onSuccess, Runnable onError) {
        mSignCheckAction = mAuthManager.isSignedObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        isSigned -> {
                            if (isSigned) {
                                onSuccess.run();
                            } else {
                                onError.run();
                            }
                        }
                );

    }

    private void subscribe(String channelId, boolean subscribed) {
        Observable<Void> observable = subscribed ? mItemManager.unsubscribeObserve(channelId) : mItemManager.subscribeObserve(channelId);

        mSubscribeAction = observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();

        MessageHelpers.showMessage(getContext(), subscribed ? R.string.unsubscribed_from_channel : R.string.subscribed_to_channel);
    }
}
