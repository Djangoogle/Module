package com.djangoogle.banner.adapter;

import android.graphics.drawable.Drawable;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.djangoogle.banner.R;
import com.djangoogle.banner.event.PlayNextAdEvent;
import com.djangoogle.banner.model.AdResourceModel;
import com.djangoogle.player.impl.OnPlayListener;
import com.djangoogle.player.manager.VLCManager;

import org.greenrobot.eventbus.EventBus;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * Created by Djangoogle on 2019/03/29 21:51 with Android Studio.
 */
public class BannerAdapter extends BaseQuickAdapter<AdResourceModel, BaseViewHolder> {

	private int mLastVisibleItemPosition = -1, mLastVisibleItemPositionCount = 0;
	private Disposable mAdPlayDisposable = null;
	private int currentType = -1;

	public BannerAdapter() {
		super(R.layout.banner);
	}

	@Override
	public void onBindViewHolder(@NonNull BaseViewHolder holder, int position, @NonNull List<Object> payloads) {
		if (payloads.isEmpty()) {
			onBindViewHolder(holder, position);
		} else {
			switch (mData.get(holder.getAdapterPosition()).type) {
				case AdResourceModel.TYPE_IMAGE:
					currentType = AdResourceModel.TYPE_IMAGE;
					//当前Item处于最上层
					if (mLastVisibleItemPosition == holder.getAdapterPosition()) {
						//开始图片广告计时任务
						startImageAdTimerTask();
					}
					break;

				case AdResourceModel.TYPE_VIDEO:
					currentType = AdResourceModel.TYPE_VIDEO;
					//开始播放视频
					loadVideoAd(holder.getAdapterPosition(), holder, ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight());
					break;

				case AdResourceModel.TYPE_MIX:
					currentType = AdResourceModel.TYPE_MIX;
					//开始播放视频
					loadVideoAd(holder.getAdapterPosition(), holder, ScreenUtils.getScreenWidth(), ScreenUtils.getScreenWidth() * 9 / 16);
					break;

				default:
					currentType = -1;
					break;
			}
		}
	}

	@Override
	protected void convert(BaseViewHolder helper, AdResourceModel item) {
		ConstraintLayout clBannerRoot = helper.getView(R.id.clBannerRoot);
		SurfaceView svBannerVideo = helper.getView(R.id.svBannerVideo);
		AppCompatImageView acivBannerImage = helper.getView(R.id.acivBannerImage);
		AppCompatImageView acivBannerVideo = helper.getView(R.id.acivBannerVideo);
		switch (item.type) {
			//图片
			case AdResourceModel.TYPE_IMAGE:
				//设置图片属性
				ConstraintLayout.LayoutParams singleImageParams =
						new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT,
								ConstraintLayout.LayoutParams.MATCH_PARENT);
				acivBannerImage.setLayoutParams(singleImageParams);
				acivBannerImage.setScaleType(ImageView.ScaleType.FIT_XY);
				acivBannerImage.setVisibility(View.VISIBLE);
				acivBannerVideo.setVisibility(View.INVISIBLE);
				//加载广告图片
				loadImageAd(helper.getAdapterPosition(), acivBannerImage);
				break;

			//视频
			case AdResourceModel.TYPE_VIDEO:
				//设置缩略图和视频属性
				ConstraintLayout.LayoutParams singleVideoParams =
						new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT,
								ConstraintLayout.LayoutParams.MATCH_PARENT);
				acivBannerVideo.setLayoutParams(singleVideoParams);
				acivBannerVideo.setScaleType(ImageView.ScaleType.FIT_XY);
				svBannerVideo.setLayoutParams(singleVideoParams);
				acivBannerVideo.setVisibility(View.VISIBLE);
				acivBannerImage.setVisibility(View.INVISIBLE);
				//加载视频广告缩略图
				loadVideoAdThumbnail(helper.getAdapterPosition(), helper.getView(R.id.acivBannerVideo));
				break;

			//图片视频混合（此处按照16:9来计算）
			case AdResourceModel.TYPE_MIX:
				int imageId = acivBannerImage.getId();
				int thumbnailId = acivBannerVideo.getId();
				int videoId = svBannerVideo.getId();
				int videoHeight = ScreenUtils.getScreenWidth() * 9 / 16;
				int imageHeight = ScreenUtils.getScreenHeight() - videoHeight;
				//设置图片宽高
				ConstraintLayout.LayoutParams mixImageParams =
						new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, imageHeight);
				acivBannerImage.setLayoutParams(mixImageParams);
				acivBannerImage.setScaleType(ImageView.ScaleType.FIT_XY);
				//设置缩略图和视频宽高
				ConstraintLayout.LayoutParams mixVideoParams =
						new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, videoHeight);
				acivBannerVideo.setLayoutParams(mixVideoParams);
				acivBannerVideo.setScaleType(ImageView.ScaleType.FIT_XY);
				svBannerVideo.setLayoutParams(mixVideoParams);
				switch (item.mixType) {
					//图片在上
					case AdResourceModel.MIX_TYPE_IMAGE_UP:
						//设置图片属性
						ConstraintSet mixImageUpConstraintSet = new ConstraintSet();
						mixImageUpConstraintSet.clone(clBannerRoot);
						mixImageUpConstraintSet.connect(imageId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
						//设置缩略图和视频属性
						ConstraintSet mixVideoDownConstraintSet = new ConstraintSet();
						mixVideoDownConstraintSet.clone(clBannerRoot);
						mixVideoDownConstraintSet.connect(thumbnailId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID,
								ConstraintSet.BOTTOM);
						mixVideoDownConstraintSet.connect(videoId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
						//应用设置
						mixImageUpConstraintSet.applyTo(clBannerRoot);
						mixVideoDownConstraintSet.applyTo(clBannerRoot);
						break;
					//视频在上
					case AdResourceModel.MIX_TYPE_VIDEO_UP:
						//设置缩略图和视频属性
						ConstraintSet mixVideoUpConstraintSet = new ConstraintSet();
						mixVideoUpConstraintSet.clone(clBannerRoot);
						mixVideoUpConstraintSet.connect(thumbnailId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
						mixVideoUpConstraintSet.connect(videoId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
						//设置图片属性
						ConstraintSet mixImageDownConstraintSet = new ConstraintSet();
						mixImageDownConstraintSet.clone(clBannerRoot);
						mixImageDownConstraintSet.connect(imageId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
						//应用设置
						mixVideoUpConstraintSet.applyTo(clBannerRoot);
						mixImageDownConstraintSet.applyTo(clBannerRoot);
						break;
					//未知类型
					default:
						//播放下一条广告
						EventBus.getDefault().post(new PlayNextAdEvent(getNextIndex(helper.getAdapterPosition())));
						return;
				}
				acivBannerVideo.setVisibility(View.VISIBLE);
				acivBannerImage.setVisibility(View.INVISIBLE);
				//加载图片广告
				loadImageAd(helper.getAdapterPosition(), acivBannerImage);
				//加载视频广告缩略图
				loadVideoAdThumbnail(helper.getAdapterPosition(), helper.getView(R.id.acivBannerVideo));
				break;

			default:
				break;
		}
	}

	@Override
	public void setNewData(@Nullable List<AdResourceModel> data) {
		//停止播放
		VLCManager.getInstance().stop();
		super.setNewData(data);
	}

	/**
	 * 设置当前Item索引
	 *
	 * @param lastVisibleItemPosition 当前Item索引
	 */
	public void setLastVisibleItemPosition(int lastVisibleItemPosition) {
		if (mLastVisibleItemPosition != lastVisibleItemPosition) {
			mLastVisibleItemPosition = lastVisibleItemPosition;
			mLastVisibleItemPositionCount = 1;
		} else {
			mLastVisibleItemPositionCount++;
		}
		//过滤重复Item
		if (mLastVisibleItemPositionCount > 1) {
			return;
		}
		//局部刷新
		notifyItemChanged(lastVisibleItemPosition, "lastVisibleItemPosition");
	}

	/**
	 * 获取下一个广告的索引
	 *
	 * @param currentPosition 当前位置
	 */
	private int getNextIndex(int currentPosition) {
		int index;
		//索引大于等于列表长度时归零
		if (currentPosition + 1 >= mData.size()) {
			index = 0;
		} else {
			//索引自增
			index = currentPosition + 1;
		}
		return index;
	}

	/**
	 * 开始图片广告计时任务
	 */
	private void startImageAdTimerTask() {
		Observable.timer(mData.get(mLastVisibleItemPosition).imageSwitchInterval, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
		          .subscribe(new Observer<Long>() {
			          @Override
			          public void onSubscribe(Disposable d) {
				          mAdPlayDisposable = d;
			          }

			          @Override
			          public void onNext(Long aLong) {
				          //播放下一条广告
				          EventBus.getDefault().post(new PlayNextAdEvent(getNextIndex(mLastVisibleItemPosition)));
			          }

			          @Override
			          public void onError(Throwable e) {
				          //播放下一条广告
				          EventBus.getDefault().post(new PlayNextAdEvent(getNextIndex(mLastVisibleItemPosition)));
			          }

			          @Override
			          public void onComplete() {}
		          });
	}

	/**
	 * 加载图片广告
	 *
	 * @param position           索引
	 * @param appCompatImageView 图片控件
	 */
	private void loadImageAd(int position, AppCompatImageView appCompatImageView) {
		String imagePath = mData.get(position).imagePath;
		LogUtils.iTag("imagePath", "图片地址: " + imagePath);
		Glide.with(mContext)
		     .load(imagePath)
		     .listener(new RequestListener<Drawable>() {
			     //图片加载失败
			     @Override
			     public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
				     //播放下一条广告
				     EventBus.getDefault().post(new PlayNextAdEvent(getNextIndex(position)));
				     return false;
			     }

			     @Override
			     public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource,
			                                    boolean isFirstResource) {
				     return false;
			     }
		     })
		     .diskCacheStrategy(DiskCacheStrategy.DATA)
		     .dontAnimate()
		     .into(appCompatImageView);
	}

	/**
	 * 加载视频广告缩略图
	 *
	 * @param position           索引
	 * @param appCompatImageView 缩略图控件
	 */
	private void loadVideoAdThumbnail(int position, AppCompatImageView appCompatImageView) {
		String videoPath = mData.get(position).videoPath;
		LogUtils.iTag("videoPath", "视频地址: " + videoPath);
		Glide.with(mContext)
		     .setDefaultRequestOptions(
				     new RequestOptions()
						     .frame(1000000)
						     .error(android.R.color.black)
						     .placeholder(android.R.color.black))
		     .load(videoPath)
		     .diskCacheStrategy(DiskCacheStrategy.DATA)//使用原图缓存
		     .dontAnimate()//取消动画
		     .into(appCompatImageView);
	}

	/**
	 * 加载视频广告
	 *
	 * @param position       索引
	 * @param baseViewHolder 轮播控件
	 * @param width          宽
	 * @param height         高
	 */
	private void loadVideoAd(int position, BaseViewHolder baseViewHolder, int width, int height) {
		String videoPath = mData.get(position).videoPath;
		VLCManager.getInstance().stop();
		VLCManager.getInstance().setView(baseViewHolder.getView(R.id.svBannerVideo));
		VLCManager.getInstance().setLocalPath(videoPath);
		VLCManager.getInstance().setSize(width, height);
		VLCManager.getInstance().addOnPlayListener(new OnPlayListener() {
			@Override
			public void onPlaying() {
				if (View.VISIBLE == baseViewHolder.getView(R.id.acivBannerVideo).getVisibility()) {
					baseViewHolder.setVisible(R.id.acivBannerVideo, false);
				}
			}

			@Override
			public void onEnded() {
				if (1 == mData.size() && (AdResourceModel.TYPE_VIDEO | AdResourceModel.TYPE_MIX) == mData.get(0).type) {
					//仅一条广告且包含视频时循环播放
					VLCManager.getInstance().play();
				} else {//播放下一条广告
					EventBus.getDefault().post(new PlayNextAdEvent(getNextIndex(position)));
				}
			}
		});
		VLCManager.getInstance().play();
	}

	/**
	 * 恢复轮播
	 */
	public void resume() {
		switch (currentType) {
			case AdResourceModel.TYPE_IMAGE:
				//重新开始播放当前图片广告
				if (mLastVisibleItemPosition >= 0) {
					//开始图片广告计时任务
					startImageAdTimerTask();
				}
				break;

			case AdResourceModel.TYPE_VIDEO:
			case AdResourceModel.TYPE_MIX:
				VLCManager.getInstance().resume();
				break;

			default:
				break;
		}
	}

	/**
	 * 暂停轮播
	 */
	public void pause() {
		switch (currentType) {
			case AdResourceModel.TYPE_IMAGE:
				if (null != mAdPlayDisposable && !mAdPlayDisposable.isDisposed()) {
					mAdPlayDisposable.dispose();
				}
				break;

			case AdResourceModel.TYPE_VIDEO:
			case AdResourceModel.TYPE_MIX:
				VLCManager.getInstance().pause();
				break;

			default:
				break;
		}
	}

	/**
	 * 释放资源
	 */
	public void destroy() {
		if (null != mAdPlayDisposable && !mAdPlayDisposable.isDisposed()) {
			mAdPlayDisposable.dispose();
		}
		VLCManager.getInstance().destroy();
	}
}
