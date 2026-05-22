package net.kdt.pojavlaunch.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CursorParser {

    /**
     * Decodes any cursor or image file (.png, .cur, .ani) from an InputStream.
     */
    public static Bitmap decodeCursor(InputStream inputStream) {
        try {
            byte[] fileBytes = readAllBytes(inputStream);
            if (fileBytes == null || fileBytes.length < 4) return null;

            // 1. Check if it's standard PNG/JPEG/etc.
            if (isPng(fileBytes) || isJpeg(fileBytes)) {
                return BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.length);
            }

            // 2. Check if it's an ANI file (RIFF format containing ICO/CUR frames)
            if (isRiffAni(fileBytes)) {
                byte[] firstFrameBytes = extractFirstAniFrame(fileBytes);
                if (firstFrameBytes != null) {
                    return decodeCur(firstFrameBytes);
                }
            }

            // 3. Try to decode as a CUR/ICO file
            return decodeCur(fileBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length >= 8 &&
                bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 &&
                bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47;
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3 &&
                bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 &&
                bytes[2] == (byte) 0xFF;
    }

    private static boolean isRiffAni(byte[] bytes) {
        return bytes.length >= 12 &&
                bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
                bytes[8] == 'A' && bytes[9] == 'C' && bytes[10] == 'O' && bytes[11] == 'N';
    }

    /**
     * Extracts the first CUR/ICO frame from an animated .ani file by searching for 'icon' chunks.
     */
    private static byte[] extractFirstAniFrame(byte[] aniBytes) {
        for (int i = 12; i < aniBytes.length - 8; i++) {
            if ((aniBytes[i] == 'i' || aniBytes[i] == 'I') &&
                (aniBytes[i+1] == 'c' || aniBytes[i+1] == 'C') &&
                (aniBytes[i+2] == 'o' || aniBytes[i+2] == 'O') &&
                (aniBytes[i+3] == 'n' || aniBytes[i+3] == 'N')) {
                
                // Read 4-byte chunk size in little-endian
                int chunkSize = (aniBytes[i+4] & 0xFF) |
                                ((aniBytes[i+5] & 0xFF) << 8) |
                                ((aniBytes[i+6] & 0xFF) << 16) |
                                ((aniBytes[i+7] & 0xFF) << 24);
                
                int startOffset = i + 8;
                if (startOffset + chunkSize <= aniBytes.length && chunkSize > 0) {
                    byte[] frameBytes = new byte[chunkSize];
                    System.arraycopy(aniBytes, startOffset, frameBytes, 0, chunkSize);
                    return frameBytes;
                }
            }
        }
        return null;
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF) |
               ((data[offset + 1] & 0xFF) << 8) |
               ((data[offset + 2] & 0xFF) << 16) |
               ((data[offset + 3] & 0xFF) << 24);
    }

    private static int readShortLE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * Decodes a CUR/ICO file byte array into a Bitmap.
     */
    private static Bitmap decodeCur(byte[] curBytes) {
        try {
            if (curBytes.length < 6) return null;

            // Validate type (1 = ICO, 2 = CUR)
            int type = readShortLE(curBytes, 2);
            if (type != 1 && type != 2) return null;

            int numImages = readShortLE(curBytes, 4);
            if (numImages <= 0) return null;

            // Read the first image entry (16 bytes)
            int entryOffset = 6;
            int width = curBytes[entryOffset] & 0xFF;
            int height = curBytes[entryOffset + 1] & 0xFF;
            if (width == 0) width = 256;
            if (height == 0) height = 256;

            int size = readIntLE(curBytes, entryOffset + 8);
            int offset = readIntLE(curBytes, entryOffset + 12);

            if (offset + size > curBytes.length) return null;

            // Extract the image data
            byte[] imageData = new byte[size];
            System.arraycopy(curBytes, offset, imageData, 0, size);

            // If the image is PNG, decode it directly
            if (isPng(imageData)) {
                return BitmapFactory.decodeByteArray(imageData, 0, size);
            }

            // Manually parse BMP DIB data (ICO/CUR format has a XOR mask and an AND mask)
            if (imageData.length < 40) return null;

            int biSize = readIntLE(imageData, 0);
            int biWidth = readIntLE(imageData, 4);
            int biHeight = readIntLE(imageData, 8);
            
            // In ICO/CUR, biHeight is double the actual height because it contains XOR and AND masks
            int actualHeight = biHeight / 2;
            if (actualHeight <= 0) actualHeight = height; // Fallback

            int biPlanes = readShortLE(imageData, 12);
            int biBitCount = readShortLE(imageData, 14);
            int biCompression = readIntLE(imageData, 16);
            int colorsUsed = readIntLE(imageData, 32);

            // Palette table
            int[] palette = null;
            if (biBitCount <= 8) {
                int numColors = colorsUsed > 0 ? colorsUsed : (1 << biBitCount);
                palette = new int[numColors];
                int paletteOffset = biSize;
                for (int i = 0; i < numColors; i++) {
                    int idx = paletteOffset + i * 4;
                    if (idx + 3 < imageData.length) {
                        int b = imageData[idx] & 0xFF;
                        int g = imageData[idx + 1] & 0xFF;
                        int r = imageData[idx + 2] & 0xFF;
                        // Build color
                        palette[i] = Color.rgb(r, g, b);
                    }
                }
            }

            int xorOffset = biSize + (palette != null ? palette.length * 4 : 0);

            // Row sizing (padded to 32-bit/4-byte boundary)
            int xorRowSize = ((width * biBitCount + 31) & ~31) / 8;
            int andRowSize = ((width * 1 + 31) & ~31) / 8;

            int andOffset = xorOffset + xorRowSize * actualHeight;

            int[] pixels = new int[width * actualHeight];

            // BMP is bottom-up format
            for (int y = 0; y < actualHeight; y++) {
                int dibY = actualHeight - 1 - y;
                int xorRowOffset = xorOffset + dibY * xorRowSize;
                int andRowOffset = andOffset + dibY * andRowSize;

                for (int x = 0; x < width; x++) {
                    int pixelColor = Color.TRANSPARENT;

                    if (biBitCount == 32) {
                        int pxOffset = xorRowOffset + x * 4;
                        if (pxOffset + 3 < imageData.length) {
                            int b = imageData[pxOffset] & 0xFF;
                            int g = imageData[pxOffset + 1] & 0xFF;
                            int r = imageData[pxOffset + 2] & 0xFF;
                            int a = imageData[pxOffset + 3] & 0xFF;
                            pixelColor = Color.argb(a, r, g, b);
                        }
                    } else if (biBitCount == 24) {
                        int pxOffset = xorRowOffset + x * 3;
                        if (pxOffset + 2 < imageData.length) {
                            int b = imageData[pxOffset] & 0xFF;
                            int g = imageData[pxOffset + 1] & 0xFF;
                            int r = imageData[pxOffset + 2] & 0xFF;
                            pixelColor = Color.rgb(r, g, b);
                        }
                    } else if (biBitCount == 8) {
                        int pxOffset = xorRowOffset + x;
                        if (pxOffset < imageData.length && palette != null) {
                            int palIdx = imageData[pxOffset] & 0xFF;
                            if (palIdx < palette.length) {
                                pixelColor = palette[palIdx];
                            }
                        }
                    } else if (biBitCount == 4) {
                        int byteOffset = xorRowOffset + (x / 2);
                        if (byteOffset < imageData.length && palette != null) {
                            int bVal = imageData[byteOffset] & 0xFF;
                            int palIdx = (x % 2 == 0) ? (bVal >> 4) : (bVal & 0x0F);
                            if (palIdx < palette.length) {
                                pixelColor = palette[palIdx];
                            }
                        }
                    } else if (biBitCount == 1) {
                        int byteOffset = xorRowOffset + (x / 8);
                        if (byteOffset < imageData.length && palette != null) {
                            int bitIdx = 7 - (x % 8);
                            int palIdx = ((imageData[byteOffset] & 0xFF) >> bitIdx) & 1;
                            if (palIdx < palette.length) {
                                pixelColor = palette[palIdx];
                            }
                        }
                    }

                    // Apply transparency AND mask (1 bit monochrome mask: 0 = opaque, 1 = transparent)
                    if (andOffset + andRowSize * actualHeight <= imageData.length) {
                        int andByteOffset = andRowOffset + (x / 8);
                        if (andByteOffset < imageData.length) {
                            int andBitIdx = 7 - (x % 8);
                            int andBit = ((imageData[andByteOffset] & 0xFF) >> andBitIdx) & 1;
                            if (andBit == 1) {
                                pixelColor = Color.TRANSPARENT;
                            }
                        }
                    }

                    pixels[y * width + x] = pixelColor;
                }
            }

            return Bitmap.createBitmap(pixels, width, actualHeight, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] readAllBytes(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    public static class AniFrame {
        public Bitmap bitmap;
        public int durationMs;
        public AniFrame(Bitmap bitmap, int durationMs) {
            this.bitmap = bitmap;
            this.durationMs = durationMs;
        }
    }

    public static List<AniFrame> decodeAniAllFrames(byte[] aniBytes) {
        List<AniFrame> list = new ArrayList<>();
        if (aniBytes == null || aniBytes.length < 12) return list;
        if (!isRiffAni(aniBytes)) return list;

        int numFrames = 0;
        int numSteps = 0;
        int defaultRate = 6; // 6 jiffies = 100ms default
        int[] seq = null;
        int[] rates = null;
        List<Bitmap> uniqueFrames = new ArrayList<>();

        int offset = 12; // skip RIFF header (4) + size (4) + ACON (4)
        while (offset < aniBytes.length - 8) {
            String chunkId = new String(aniBytes, offset, 4);
            int chunkSize = readIntLE(aniBytes, offset + 4);
            int nextOffset = offset + 8 + chunkSize;
            if (chunkSize % 2 != 0) {
                nextOffset++;
            }

            if (nextOffset > aniBytes.length) {
                break;
            }

            int chunkDataOffset = offset + 8;
            if ("anih".equals(chunkId)) {
                if (chunkSize >= 36) {
                    numFrames = readIntLE(aniBytes, chunkDataOffset + 4);
                    numSteps = readIntLE(aniBytes, chunkDataOffset + 8);
                    defaultRate = readIntLE(aniBytes, chunkDataOffset + 28);
                }
            } else if ("seq ".equals(chunkId)) {
                int count = chunkSize / 4;
                if (count > 0) {
                    seq = new int[count];
                    for (int i = 0; i < count; i++) {
                        seq[i] = readIntLE(aniBytes, chunkDataOffset + i * 4);
                    }
                }
            } else if ("rate".equals(chunkId)) {
                int count = chunkSize / 4;
                if (count > 0) {
                    rates = new int[count];
                    for (int i = 0; i < count; i++) {
                        rates[i] = readIntLE(aniBytes, chunkDataOffset + i * 4);
                    }
                }
            } else if ("LIST".equals(chunkId)) {
                if (chunkSize >= 4) {
                    String listType = new String(aniBytes, chunkDataOffset, 4);
                    if ("fram".equals(listType)) {
                        int listOffset = chunkDataOffset + 4;
                        int listEnd = chunkDataOffset + chunkSize;
                        while (listOffset < listEnd - 8) {
                            String subChunkId = new String(aniBytes, listOffset, 4);
                            int subChunkSize = readIntLE(aniBytes, listOffset + 4);
                            int subNextOffset = listOffset + 8 + subChunkSize;
                            if (subChunkSize % 2 != 0) {
                                subNextOffset++;
                            }
                            if (subNextOffset > listEnd) {
                                break;
                            }

                            if ("icon".equals(subChunkId)) {
                                byte[] iconBytes = new byte[subChunkSize];
                                System.arraycopy(aniBytes, listOffset + 8, iconBytes, 0, subChunkSize);
                                Bitmap bmp = decodeCur(iconBytes);
                                if (bmp != null) {
                                    uniqueFrames.add(bmp);
                                }
                            }
                            listOffset = subNextOffset;
                        }
                    }
                }
            }

            offset = nextOffset;
        }

        if (uniqueFrames.isEmpty()) return list;

        int stepsCount = (numSteps > 0) ? numSteps : uniqueFrames.size();
        if (seq == null) {
            seq = new int[stepsCount];
            for (int i = 0; i < stepsCount; i++) {
                seq[i] = i % uniqueFrames.size();
            }
        }

        for (int i = 0; i < seq.length; i++) {
            int frameIdx = seq[i];
            if (frameIdx >= 0 && frameIdx < uniqueFrames.size()) {
                Bitmap bmp = uniqueFrames.get(frameIdx);
                int jiffies = defaultRate;
                if (rates != null && i < rates.length) {
                    jiffies = rates[i];
                }
                int durationMs = Math.max(16, (int) (jiffies * 1000f / 60f));
                list.add(new AniFrame(bmp, durationMs));
            }
        }

        return list;
    }
}
