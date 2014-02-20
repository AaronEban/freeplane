/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2009 Dimitry
 *
 *  This file author is Dimitry
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.core.ui.components;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JComponent;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;
import org.freeplane.view.swing.features.filepreview.ScalableComponent;

import com.thebuzzmedia.imgscalr.AsyncScalr;

/**
 * @author Dimitry Polivaev
 * 22.08.2009
 */
public class BitmapViewerComponent extends JComponent implements ScalableComponent {
	static {
		AsyncScalr.setServiceThreadCount(1);
	}

	enum CacheType {
		IC_DISABLE, IC_FILE, IC_RAM
	}

	private static final long serialVersionUID = 1L;
	private File cacheFile;
	private int hint;
	private BufferedImage cachedImage;
	private WeakReference<BufferedImage> cachedImageWeakRef;
	private final URL url;
	private final Dimension originalSize;
	private int imageX;
	private int imageY;
	private boolean processing;
	private boolean scaleEnabled;
	private Shape clip = null;
	private final static Object LOCK = new Object();

	public BitmapViewerComponent(final URI uri) throws MalformedURLException,
			IOException {
		url = uri.toURL();
		originalSize = readOriginalSize();
		hint = Image.SCALE_SMOOTH;
		processing = false;
		scaleEnabled = true;
		cachedImage = null;
	}

	private Dimension readOriginalSize() throws IOException {
		InputStream inputStream = null;
		ImageInputStream in = null;
		try {
			inputStream = url.openStream();
			in = ImageIO.createImageInputStream(inputStream);
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (readers.hasNext()) {
				final ImageReader reader = readers.next();
				try {
					reader.setInput(in);
					return new Dimension(reader.getWidth(0),
							reader.getHeight(0));
				} finally {
					reader.dispose();
				}
			} else {
				throw new IOException("can not create image");
			}
		} finally {
			if (in != null) {
				in.close();
			}
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	public boolean isScaleEnabled() {
		return scaleEnabled;
	}

	public void setScaleEnabled(final boolean scaleEnabled) {
		this.scaleEnabled = scaleEnabled;
	}

	protected int getHint() {
		return hint;
	}

	public void setHint(final int hint) {
		this.hint = hint;
	}

	public Dimension getOriginalSize() {
		return new Dimension(originalSize);
	}

	@Override
	protected void paintComponent(final Graphics g) {
		if (processing || componentHasNoArea()) {
			return;
		}
		if (cachedImage == null && cachedImageWeakRef != null) {
			cachedImage = cachedImageWeakRef.get();
			cachedImageWeakRef = null;
		}
		if (cachedImage == null && cacheFile != null) {
			loadImageFromCacheFile();
			if (!isCachedImageValid()) {
				cacheFile.delete();
				cacheFile = null;
			}
		}
		if (isCachedImageValid()) {
			//			if (clip != null) {
			//				g.setClip(clip);
			//				//							Rectangle clipRect = (Rectangle) clip;
			//				//							g.clipRect(clipRect.x, clipRect.y, clipRect.width, clipRect.height);
			//			}
			g.drawImage(cachedImage, imageX, imageY, null);
			flushImage();
		} else {
			final BufferedImage image = loadImageFromURL();
			if (image == null || hasNoArea(image)) {
				return;
			}
			//			Graphics imageGraphics = image.getGraphics();
			//			if (clip != null) {
			//				Rectangle clipRect = (Rectangle) clip;
			//				imageGraphics.clipRect(clipRect.x, clipRect.y, clipRect.width, clipRect.height);
			//			}
			processing = true;
			final Future<BufferedImage> result = AsyncScalr.resize(image, getWidth(), getHeight());
			AsyncScalr.getService().submit(new Runnable() {
				public void run() {
					BufferedImage scaledImage = null;
					try {
						scaledImage = result.get();
					} catch (final Exception e) {
						LogUtils.severe(e);
						return;
					} finally {
						image.flush();
					}
					final int scaledImageHeight = scaledImage.getHeight();
					final int scaledImageWidth = scaledImage.getWidth();
					centerImagePosition(scaledImageHeight, scaledImageWidth);
					cachedImage = scaledImage;
					if (getCacheType().equals(CacheType.IC_FILE)) {
						writeCacheFile();
					}
					EventQueue.invokeLater(new Runnable() {
						public void run() {
							processing = false;
							repaint();
						}
					});
				}

				private void centerImagePosition(final int scaledImageHeight,
						final int scaledImageWidth) {
					if (scaledImageHeight > getHeight()) {
						imageX = 0;
						imageY = (getHeight() - scaledImageHeight) / 2;
					}
					else {
						imageX = (getWidth() - scaledImageWidth) / 2;
						imageY = 0;
					}
				}
			});
		}
	}

	private boolean componentHasNoArea() {
		return getWidth() == 0 || getHeight() == 0;
	}

	private boolean hasNoArea(final BufferedImage image) {
		final int imageWidth = image.getWidth();
		final int imageHeight = image.getHeight();
		if (imageWidth == 0 || imageHeight == 0) {
			return true;
		}
		return false;
	}

	private void loadImageFromCacheFile() {
		try {
			cachedImage = ImageIO.read(cacheFile);
		} catch (final IOException e) {
			LogUtils.severe(e);
		}
	}

	private boolean isCachedImageValid() {
		return cachedImage != null
				&& (!scaleEnabled 
						|| componentHasAlmostSameWidthAsCachedImage() && componentHasLargerHeightThanCachedImage() 
						|| componentHasLargerWidthThanCachedImage() && componentHasAlmostSameHeightAsCachedImage());
	}

	private boolean componentHasAlmostSameHeightAsCachedImage() {
		return 1 >= Math.abs(getHeight() - cachedImage.getHeight());
	}

	private boolean componentHasLargerWidthThanCachedImage() {
		return getWidth() >= cachedImage.getWidth();
	}

	private boolean componentHasLargerHeightThanCachedImage() {
		return getHeight() >= cachedImage.getHeight();
	}

	private boolean componentHasAlmostSameWidthAsCachedImage() {
		return 1 >= Math.abs(getWidth() - cachedImage.getWidth());
	}

	private BufferedImage loadImageFromURL() {
		BufferedImage tempImage = null;
		try {
			tempImage = ImageIO.read(url);
		} catch (final IOException e) {
			LogUtils.severe(e);
		}
		return tempImage;
	}

	private void flushImage() {
		final CacheType cacheType = getCacheType();
		if (CacheType.IC_RAM.equals(cacheType)) {
			cachedImage.flush();
		}
		else {
			cachedImageWeakRef = new WeakReference<BufferedImage>(cachedImage);
			cachedImage = null;
		}
	}

	private CacheType getCacheType() {
		return ResourceController.getResourceController().getEnumProperty("image_cache", CacheType.IC_DISABLE);
	}

	private void writeCacheFile() {
		final File tempDir = new File(System.getProperty("java.io.tmpdir"),
				"freeplane");
		tempDir.mkdirs();
		try {
			synchronized (LOCK) {
				cacheFile = File.createTempFile("cachedImage", ".jpg", tempDir);
			}
			ImageIO.write(cachedImage, "jpg", cacheFile);
		} catch (final IOException e) {
			cacheFile.delete();
			cacheFile = null;
		}
	}

	@Override
	public void removeNotify() {
		super.removeNotify();
		if (cacheFile != null) {
			cacheFile.delete();
			cacheFile = null;
		}
	}

	public void setFinalViewerSize(final Dimension size) {
		setPreferredSize(size);
		setSize(size);
		//		correctRatioAndSetSize(size);
		setScaleEnabled(true);
	}

	public void setDraftViewerSize(final Dimension size) {
		setPreferredSize(size);
		setSize(size);
		setScaleEnabled(false);
	}

	public void setFinalViewerSize(final float zoom) {
		int scaledWidth = (int) (originalSize.width * zoom);
		int scaledHeight = (int) (originalSize.height * zoom);
		setFinalViewerSize(new Dimension(scaledWidth, scaledHeight));
	}

	public void setClip(final Shape clip) {
		this.clip = clip;
	}

	//	private void correctRatioAndSetSize(final Dimension size) {
	//		int originalRatio = getOriginalSize().width / getOriginalSize().height;
	//		int currentRatio = size.width / size.height;
	//		if (originalRatio != currentRatio) {
	//
	//		}
	//		setSize(size);
	//	}

}


