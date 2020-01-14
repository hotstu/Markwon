package io.noties.markwon.image;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AsyncDrawable extends Drawable {

    private final String destination;
    private final AsyncDrawableLoader loader;
    private final ImageSize imageSize;
    private final ImageSizeResolver imageSizeResolver;

    private Drawable result;
    private Callback callback;

    private int canvasWidth;
    private float textSize;

    // @since 2.0.1 for use-cases when image is loaded faster than span is drawn and knows canvas width
    private boolean waitingForDimensions;

    /**
     * @since 1.0.1
     */
    public AsyncDrawable(
            @NonNull String destination,
            @NonNull AsyncDrawableLoader loader,
            @NonNull ImageSizeResolver imageSizeResolver,
            @Nullable ImageSize imageSize
    ) {
        this.destination = destination;
        this.loader = loader;
        this.imageSizeResolver = imageSizeResolver;
        this.imageSize = imageSize;

        final Drawable placeholder = loader.placeholder(this);
        if (placeholder != null) {
            setPlaceholderResult(placeholder);
        }
    }

    @NonNull
    public String getDestination() {
        return destination;
    }

    /**
     * @since 4.0.0
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    public ImageSize getImageSize() {
        return imageSize;
    }

    /**
     * @since 4.0.0
     */
    @SuppressWarnings("unused")
    @NonNull
    public ImageSizeResolver getImageSizeResolver() {
        return imageSizeResolver;
    }

    /**
     * @see #hasKnownDimensions()
     * @since 4.0.0
     * @deprecated 4.2.1-SNAPSHOT
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    @Deprecated
    public boolean hasKnownDimentions() {
        return canvasWidth > 0;
    }

    /**
     * @since 4.2.1-SNAPSHOT
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public boolean hasKnownDimensions() {
        return canvasWidth > 0;
    }

    /**
     * @see #hasKnownDimensions()
     * @since 4.0.0
     */
    public int getLastKnownCanvasWidth() {
        return canvasWidth;
    }

    /**
     * @see #hasKnownDimensions()
     * @since 4.0.0
     */
    @SuppressWarnings("WeakerAccess")
    public float getLastKnowTextSize() {
        return textSize;
    }

    public Drawable getResult() {
        return result;
    }

    @SuppressWarnings("WeakerAccess")
    public boolean hasResult() {
        return result != null;
    }

    public boolean isAttached() {
        return getCallback() != null;
    }

    // yeah
    @SuppressWarnings("WeakerAccess")
    public void setCallback2(@Nullable Callback cb) {

        // @since 4.2.1-SNAPSHOT
        //  wrap callback so invalidation happens to this AsyncDrawable instance
        //  and not for wrapped result/placeholder
        this.callback = cb == null
                ? null
                : new WrappedCallback(cb);

        super.setCallback(cb);

        // if not null -> means we are attached
        if (callback != null) {

            // as we have a placeholder now, it's important to check it our placeholder
            // has a proper callback at this point. This is not required in most cases,
            // as placeholder should be static, but if it's not -> it can operate as usual
            if (result != null
                    && result.getCallback() == null) {
                result.setCallback(callback);
            }

            loader.load(this);
        } else {
            if (result != null) {

                result.setCallback(null);

                // let's additionally stop if it Animatable
                if (result instanceof Animatable) {
                    ((Animatable) result).stop();
                }
            }
            loader.cancel(this);
        }
    }

    /**
     * @since 3.0.1
     */
    @SuppressWarnings("WeakerAccess")
    protected void setPlaceholderResult(@NonNull Drawable placeholder) {
        // okay, if placeholder has bounds -> use it, otherwise use original imageSize

        final Rect rect = placeholder.getBounds();

        if (rect.isEmpty()) {
            // if bounds are empty -> just use placeholder as a regular result
            DrawableUtils.applyIntrinsicBounds(placeholder);
            setResult(placeholder);
        } else {

            // this condition should not be true for placeholder (at least for now)
            if (result != null) {
                // but it is, unregister current result
                result.setCallback(null);
            }

            // placeholder has bounds specified -> use them until we have real result
            this.result = placeholder;
            this.result.setCallback(callback);

            // use bounds directly
            setBounds(rect);

            // just in case -> so we do not update placeholder when we have canvas dimensions
            waitingForDimensions = false;
        }
    }

    public void setResult(@NonNull Drawable result) {

        // if we have previous one, detach it
        if (this.result != null) {
            this.result.setCallback(null);
        }

        this.result = result;

        initBounds();
    }

    /**
     * Remove result from this drawable (for example, in case of cancellation)
     *
     * @since 3.0.1
     */
    public void clearResult() {

        final Drawable result = this.result;

        if (result != null) {
            result.setCallback(null);
            this.result = null;

            // clear bounds
            setBounds(0, 0, 0, 0);
        }
    }

    private void initBounds() {

        if (canvasWidth == 0) {
            // we still have no bounds - wait for them
            waitingForDimensions = true;
            return;
        }

        waitingForDimensions = false;

        final Rect bounds = resolveBounds();
        result.setBounds(bounds);
        // @since 4.2.1-SNAPSHOT, we set callback after bounds are resolved
        //  to reduce number of invalidations
        result.setCallback(callback);

        // so, this method will check if there is previous bounds and call invalidate _BEFORE_
        //  applying new bounds. This is why it is important to have initial bounds empty.
        setBounds(bounds);

        invalidateSelf();
    }

    /**
     * @since 1.0.1
     */
    @SuppressWarnings("WeakerAccess")
    public void initWithKnownDimensions(int width, float textSize) {
        this.canvasWidth = width;
        this.textSize = textSize;

        if (waitingForDimensions) {
            initBounds();
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (hasResult()) {
            result.draw(canvas);
        }
    }

    @Override
    public void setAlpha(@IntRange(from = 0, to = 255) int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        final int opacity;
        if (hasResult()) {
            opacity = result.getOpacity();
        } else {
            opacity = PixelFormat.TRANSPARENT;
        }
        return opacity;
    }

    @Override
    public int getIntrinsicWidth() {
        final int out;
        if (hasResult()) {
            out = result.getIntrinsicWidth();
        } else {
            // @since 4.0.0, must not be zero in order to receive canvas dimensions
            out = 1;
        }
        return out;
    }

    @Override
    public int getIntrinsicHeight() {
        final int out;
        if (hasResult()) {
            out = result.getIntrinsicHeight();
        } else {
            // @since 4.0.0, must not be zero in order to receive canvas dimensions
            out = 1;
        }
        return out;
    }

    /**
     * @since 1.0.1
     */
    @NonNull
    private Rect resolveBounds() {
        // @since 2.0.0 previously we were checking if image is greater than canvas width here
        //          but as imageSizeResolver won't be null anymore, we should transfer this logic
        //          there
        return imageSizeResolver.resolveImageSize(this);
    }

    @NonNull
    @Override
    public String toString() {
        return "AsyncDrawable{" +
                "destination='" + destination + '\'' +
                ", imageSize=" + imageSize +
                ", result=" + result +
                ", canvasWidth=" + canvasWidth +
                ", textSize=" + textSize +
                ", waitingForDimensions=" + waitingForDimensions +
                '}';
    }

    // @since 4.2.1-SNAPSHOT
    //  Wrapped callback to trigger invalidation for this AsyncDrawable instance (and not result/placeholder)
    private class WrappedCallback implements Callback {

        private final Callback callback;

        WrappedCallback(@NonNull Callback callback) {
            this.callback = callback;
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            callback.invalidateDrawable(AsyncDrawable.this);
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            callback.scheduleDrawable(AsyncDrawable.this, what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            callback.unscheduleDrawable(AsyncDrawable.this, what);
        }
    }
}
