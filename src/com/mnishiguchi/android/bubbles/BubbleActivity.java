package com.mnishiguchi.android.bubbles;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

public class BubbleActivity extends Activity
{
	private static final String TAG = "Bubble";
	
	private final static int RANDOM = 0;
	private final static int SINGLE = 1;
	private final static int STILL = 2;
	private static int speedMode = RANDOM;

	// The Main view
	private RelativeLayout mFrame;

	// Bubble image's bitmap
	private Bitmap mBitmap;

	// Display dimensions
	private int mDisplayWidth, mDisplayHeight;

	/*
	 * Sound variables
	 */
	
	// AudioManager
	private AudioManager mAudioManager;
	// SoundPool
	private SoundPool mSoundPool;
	// ID for the bubble popping sound
	private int mSoundID;
	// Audio volume
	private float mStreamVolume;

	// Gesture Detector
	private GestureDetector mGestureDetector;

	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		Log.i(TAG, ":entered onCrate()");
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Set up user interface
		mFrame = (RelativeLayout) findViewById(R.id.frame);

		// Load basic bubble Bitmap
		mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.b64);
	}

	@Override
	protected void onResume()
	{
		Log.i(TAG, ":entered onResume()");
		super.onResume();

		// Manage bubble popping sound
		// stream type: AudioManager.STREAM_MUSIC
		mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

		// Current volume / Max volume (0f..1f)
		mStreamVolume = (float) 
				mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
				/ mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		// Create a new SoundPool, allowing up to 10 streams.
		mSoundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);

		// Set a SoundPool#OnLoadCompletedListener.
		mSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {

			@Override
			public void onLoadComplete(SoundPool soundPool, int sampleId, int status)
			{
				Log.i(TAG, ":entered onLoadComplete()");
				
				setupGestureDetector();
			}
		});
		
		// Load the sound effect from res/raw/bubble_pop.wav
		mSoundID = mSoundPool.load(this, R.raw.bubble_pop, 1);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		Log.i(TAG, ":entered onWindowFocusChanged()");
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus)
		{
			// Get the size of the display.
			mDisplayWidth = mFrame.getWidth();
			mDisplayHeight = mFrame.getHeight();
		}
	}

	/**
	 * Set up GestureDetector. If a fling gesture starts on a BubbleView
	 * then change the BubbleView's velocity (pixels per second).
	 */
	private void setupGestureDetector()
	{
		Log.i(TAG, ":entered setupGestureDetector()");
		
		mGestureDetector = new GestureDetector(this,
			new GestureDetector.SimpleOnGestureListener() {

			// If a fling gesture starts on a BubbleView
			// then change the BubbleView's velocity.(in pixels per second)
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2,
					float velocityX, float velocityY)
			{
				Log.i(TAG, ":entered onFling()");

				// Iterate over all Views in the frame.
				// Check if the first down motion intersects a bubble.
				BubbleView bubble;
				for (int i = 0, size = mFrame.getChildCount();
						i < size; i++)
				{
					bubble = (BubbleView)mFrame.getChildAt(i);
					if (bubble.intersects(e1.getX(), e1.getY())
							|| bubble.intersects(e2.getX(), e2.getY()))
					{
						// Change the Bubble's speed and direction.
						bubble.deflect(velocityX, velocityY);
					}
				}
				return true; // The motion event was consumed.
			}

			// If a single tap intersects a BubbleView, then pop the BubbleView.
			// Otherwise, create a new BubbleView at the tap's location and
			// add it to mFrame. 
			@Override
			public boolean onSingleTapConfirmed(MotionEvent event)
			{
				Log.i(TAG, ":entered onSingleTapConfirmed()");
				
				boolean isTappedBubble = false;
				BubbleView bubble = null;
				
				// Iterate over all Views in the frame.
				for (int i = 0, size = mFrame.getChildCount();
						i < size; i++)
				{
					// Check if the tap intersects any bubble.
					bubble = (BubbleView) mFrame.getChildAt(i);	
					if (bubble.intersects(event.getX(),event.getY()))
					{
						isTappedBubble = true;
						break;
					}
				}
				
				if (isTappedBubble)
				{
					bubble.stopMovement(true);
				}
				else
				{
					// Create a new babble.
					BubbleView newBubble = new BubbleView(getApplicationContext(),
							event.getX(),event.getY());
					
					// Add it to the frame.
					mFrame.addView(newBubble);
					
					// Make it start moving.
					newBubble.startMovement();
				}

				return true; // The motion event was consumed.
			}
		});
	}

	// Delegate the touch to the gestureDetector.
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		return mGestureDetector.onTouchEvent(event);
	}

	@Override
	protected void onPause()
	{
		Log.i(TAG, ":entered onPause()");
		
		// Release all SoundPool resources
		if (null != mSoundPool)
		{
			mSoundPool.unload(mSoundID);
			mSoundPool.release();
			mSoundPool = null;
		}

		super.onPause();
	}

	/**
	 * BubbleView is a View that displays a bubble.
	 * This class handles animating, drawing and popping among other actions.
	 * A new BubbleView is created for each bubble on the display.
	 */
	public class BubbleView extends View
	{
		private static final int BITMAP_SIZE = 64;
		private static final int REFRESH_RATE = 40;
		private static final int TAP_SENSITIVITY = 5;
		
		// Set colors and styles for the drawing.
		private final Paint mPainter = new Paint();
		
		// Remember scheduled tasks.
		private ScheduledFuture<?> mScheduledTasks;
		
		// Scaled Bitmap
		private int mScaledBitmapWidth;
		private Bitmap mScaledBitmap;

		// Bubble's attributes.
		private float mXPos, mYPos; // Top-left corner of the bitmap.
		private float mDx, mDy; // speed & direction.
		private float mRadius; // To adjust bitmap position.
		private float mRadiusSquared; // To calculate distance.
		private long mRotate; // current rotating position (in degree).
		private long mDRotate; // rotating rate (in degree).
		
		/**
		 * Constructor.
		 * @param context
		 * @param ox The x coordinate of the bubble's origin.
		 * @param oy The y coordinate of the bubble's origin.
		 */
		BubbleView(Context context, float ox, float oy)
		{
			super(context);

			// Create a new random number generator to
			// randomize size, rotation, speed and direction.
			Random r = new Random();

			// Creates the bubble bitmap for this BubbleView.
			createScaledBitmap(r);

			// Radius of the Bitmap
			mRadius = mScaledBitmapWidth / 2;
			mRadiusSquared = mRadius * mRadius;
			
			// Adjust position to center the bubble under user's finger.
			mXPos = ox - mRadius;
			mYPos = oy - mRadius;

			// Set the BubbleView's initial speed and direction
			initSpeedAndDirection(r);

			// Set the BubbleView's initial rotation
			initRotation(r);

			mPainter.setAntiAlias(true);
		}

		/**
		 * Set initial rotation randomly in the range of 1 to 3 inclusive.
		 * @param random an instance of Random.
		 */
		private void initRotation(Random random)
		{
			if (speedMode == RANDOM)
			{
				// Set rotation in range [1..3].
				mDRotate = random.nextInt(3) + 1;
			}
			else
			{
				mDRotate = 0;
			}
		}
		
		/**
		 * Set randomly initial mDx and mDy to indicate movement direction and speed.
		 * Limit speed in the x and y direction to [-3..3] pixels per movement.
		 * @param r an instance of Random.
		 */
		private void initSpeedAndDirection(Random r)
		{
			// Used by test cases
			switch (speedMode)
			{
				case SINGLE:
					mDx = 20;
					mDy = 20;
					break;
				
				case STILL:
					// No speed
					mDx = 0;
					mDy = 0;
					break;
				
				default:
					// Random in range [-3..3] pixels per movement.
					mDx = r.nextInt(7) - 3;
					mDy = r.nextInt(7) - 3;
			}
		}

		/**
		 * Set scaled bitmap size, randomly in range [1..3] * BITMAP_SIZE.
		 * Create the scaled bitmap using size set above.
		 * @param random an instance of Random.
		 */
		private void createScaledBitmap(Random random)
		{
			if (speedMode != RANDOM)
			{
				mScaledBitmapWidth = BITMAP_SIZE * 3;
			}
			else
			{
				// Set scaled bitmap size in range [1..3] * BITMAP_SIZE.
				mScaledBitmapWidth = BITMAP_SIZE * (random.nextInt(3) + 1);
			}

			// Create the scaled bitmap using size set above.
			mScaledBitmap = Bitmap.createScaledBitmap(mBitmap,
					mScaledBitmapWidth, mScaledBitmapWidth, false);
		}

		/**
		 * Start moving the BubbleView & updating the display.
		 * Each time this method is run, the BubbleView will move one step.
		 * If the BubbleView exits the display, stop its Worker Thread.
		 * Otherwise, request that the BubbleView be redrawn.
		 */
		private void startMovement()
		{
			Log.i(TAG, ":entered startMovement()");
			
			// Creates a WorkerThread.
			ScheduledExecutorService executor = 
					Executors.newScheduledThreadPool(1);

			// Execute the run() in Worker Thread every REFRESH_RATE.
			// Save reference to this job in mMoverFuture.
			mScheduledTasks = executor.scheduleWithFixedDelay(new Runnable() {
				
				@Override
				public void run()
				{
					Log.i(TAG, ":entered run()");

					if (BubbleView.this.moveThenIsStillOnScreen()) // Still on the screen.
					{
						// Request that the BubbleView be redrawn.
						// Use postInvalidate() because this is a non-UI thread.
						BubbleView.this.postInvalidate();
					}
					else // the BubbleView exited the screen.
					{
						// Stop the BubbleView's Worker Thread.
						BubbleView.this.stopMovement(false);
					}
				}
			}, 0, REFRESH_RATE, TimeUnit.MILLISECONDS);
		}

		/**
		 * Check if a given point (x ,y) is within the boundary of the BubbleView.
		 * @param x the x coordinate of the BubbleView's origin.
		 * @param y the y coordinate of the BubbleView's origin.
		 * @return true if the BubbleView intersects the point (x,y)
		 */
		private synchronized boolean intersects(float x, float y)
		{
			Log.i(TAG, ":entered intersects()");
			
			// If a point(x, y) is within a circle(ox, oy), it must satisfy:
			// (x - ox)^2 + (y - oy)^2 < radius^2.
			return (x - mXPos) * (x - mXPos) + (y - mYPos) * (y - mYPos)
					<= mRadiusSquared * TAP_SENSITIVITY;
		}

		/**
		 * Cancel the Bubble's movement. Remove Bubble from mFrame.
		 * Play pop sound if the BubbleView was popped.
		 */
		private void stopMovement(final boolean wasPopped)
		{
			Log.i(TAG, ":entered stopMovement()");
			
			if (null != mScheduledTasks) // Null check.
			{
				// Cancel the tasks if not already done.
				if (!mScheduledTasks.isDone())
				{
					mScheduledTasks.cancel(true);
				}

				// This work will be performed on the UI Thread.
				mFrame.post(new Runnable()
				{
					@Override
					public void run()
					{
						// Remove the BubbleView from mFrame
						mFrame.removeView(BubbleView.this);

						// Play the popping sound
						if (wasPopped)
						{
							mSoundPool.play(mSoundID, mStreamVolume,
									mStreamVolume, 1, 0, 1.0f);
						}
					}
				});
			}
		}
		
		/**
		 * Change the Bubble's speed and direction based on the specified velocities.
		 * @param velocityX (in pixel per second)
		 * @param velocityY (in pixel per second)
		 */
		private synchronized void deflect(float velocityX, float velocityY)
		{
			Log.i(TAG, ":entered deflect()");
			
			// Calculate the deltas per REFRESH_RATE.
			mDx = velocityX / REFRESH_RATE;
			mDy = velocityY / REFRESH_RATE;
		}

		/**
		 * Draw the Bubble at its current location.
		 * Called when requested via postInvalidate().
		 */
		@Override
		protected synchronized void onDraw(Canvas canvas)
		{
			Log.i(TAG, ":entered onDraw()");
			
			// Save the canvas
			canvas.save();
			
			// Increase the rotation degree by mDRotate.
			mRotate += mDRotate;

			// Rotate the canvas by current rotation.
			// Set the pivot to the center of the bitmap.
			canvas.rotate(mRotate, mXPos + mRadius, mYPos + mRadius);

			// Draw the bitmap at it's updated location on the canvas.
			canvas.drawBitmap(mScaledBitmap, mXPos, mYPos, mPainter);

			// restore the canvas
			canvas.restore();
		}
		
		/**
		 * Move the BubbleView (setting speed and direction).
		 * @return true if the BubbleView is still on the screen
		 * after the move operation.
		 */
		private synchronized boolean moveThenIsStillOnScreen()
		{
			// Move the BubbleView
			// set speed and direction
			mXPos += mDx;
			mYPos += mDy;
			
			bounceOnWall();
			
			return isOnScreen();
		}

		/**
		 * Return true if the BubbleView is still on the screen.
		 */
		private boolean isOnScreen()
		{
			boolean isOutTop = mYPos < (0 - mRadius * 2);
			boolean isOutLeft = mXPos < (0 - mRadius * 2);
			boolean isOutBottom = mDisplayHeight < mYPos;
			boolean isOutRight = mDisplayWidth < mXPos;
			
			if (isOutLeft || isOutRight || isOutTop || isOutBottom)
			{
				return false;  // Outside the frame.
			}
			else
			{
				return true;  // Inside the frame.
			}
		}
		
		/**
		 * Bounce off when hitting the walls or ceiling.
		 */
		private synchronized void bounceOnWall()
		{
			// bounce off top edge of window
			if (mYPos <= 0)
			{
				mDy = -mDy;
			}
			// bounce off left edge of window
			else if (mXPos <= 0)
			{
				mDx = -mDx;
			}
			// bounce off right edge of window
			else if (mXPos + (2 * mRadius) >= mDisplayWidth)
			{
				mDx = -mDx;
			} 
		}
	}

	@Override
	public void onBackPressed()
	{
		// Programmatically opens the options menu.
		openOptionsMenu();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menu_still_mode:
				speedMode = STILL;
				return true;
			case R.id.menu_single_speed:
				speedMode = SINGLE;
				return true;
			case R.id.menu_random_mode:
				speedMode = RANDOM;
				return true;
			case R.id.quit:
				exitRequested();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Finish the activity
	 */
	private void exitRequested()
	{
		// Invoke the default implementation of onBackPressed(),
		super.onBackPressed();
	}
}
