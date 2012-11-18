
package jp.inara.mediacodecapisample;

import java.nio.ByteBuffer;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;

/**
 * MediaCodecAPIを使って動画から音声のみを取り出すサンプルコード
 * 
 * @author inara
 */
public class MainActivity extends Activity {

    private static final String LOG_TAG = "MainActivity";
    
    /** バッファを取り出す際のタイムアウト時間(マイクロ秒) */
    private static final long TIMEOUT_US = 1;
    
    /** 音声再生用のAudioTrack */
    private AudioTrack mAudioTrack;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // assetsからメディアファイルを読み込む
        AssetFileDescriptor descriptor = getResources().openRawResourceFd(
                R.raw.sample);

        // MediaExtractorでファイルからフォーマット情報を抽出。
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(descriptor.getFileDescriptor(),
                descriptor.getStartOffset(), descriptor.getLength());

        // トラック数
        // 動画の場合、トラック1が映像、トラック2が音声？
        Log.d(LOG_TAG, String.format("TRACKS #: %d", extractor.getTrackCount()));

        // 音声のMime Type
        MediaFormat format = extractor.getTrackFormat(1);
        String mime = format.getString(MediaFormat.KEY_MIME);
        Log.d(LOG_TAG, String.format("Audio MIME TYPE: %s", mime));
        

        // デコーターを作成する
        MediaCodec codec;
        codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        // デコーダーからInputBuffer, OutputBufferを取得する
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();

        // 読み込むトラック番号を指定する
        // ここでは音声を指定
        extractor.selectTrack(1);

        // AudioTrac生成用にメディアから情報取得
        // サンプリングレート
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        // バッファの最大バイトサイズ
        int maxSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        
        // AudioTrackのインスタンス生成
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, // 音楽再生用のオーディオストリーム
                sampleRate, // サンプリングレート
                AudioFormat.CHANNEL_OUT_MONO, // モノラル
                AudioFormat.ENCODING_PCM_16BIT, // フォーマット
                maxSize,// 合計バッファサイズ
                AudioTrack.MODE_STREAM); // ストリームモード
        // 再生開始
        mAudioTrack.play();

        // インプットバッファがEnd Of Streamかどうかを判定するフラグ
        boolean sawInputEOS = false;
        
        // 以下、バッファの処理。インプットバッファの数だけ繰り返す
        for (;;) {
            // TIMEOUT_USが 0 だと待ち時間なしで即結果を返す。
            // 負の値で無限に応答を待つ
            // 正の値だと 値 microseconds分だけ待つ
            int inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US);
            
            // Log.d(LOG_TAG, String.format("Input Buffer Index =  %d",
            // inputBufIndex));
            
            if (inputBufIndex >= 0) {
                // インプットバッファの配列から対象のバッファを取得
                ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                // バッファサイズ
                int bufferSize = extractor.readSampleData(dstBuf, 0);
                long presentationTimeUs = 0;
                if (bufferSize < 0) {
                    sawInputEOS = true;
                    bufferSize = 0;
                } else {
                    presentationTimeUs = extractor.getSampleTime();
                }
                
                // デコード処理してアウトプットバッファに追加？
                codec.queueInputBuffer(inputBufIndex, 0, bufferSize, presentationTimeUs,
                        sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                if (!sawInputEOS) {
                    extractor.advance();
                } else {
                    break;
                }
            }

            // 出力処理
            
            MediaCodec.BufferInfo info = new BufferInfo();
            int outputBufIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            // Log.d(LOG_TAG, String.format("Output Buffer Index =  %d",
            // outputBufIndex));
            if (outputBufIndex >= 0) {
                // ここで出力処理をする
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                // チャンクを作る
                final byte[] chunk = new byte[info.size];
                buf.get(chunk);
                buf.clear();

                // AudioTrackに書き込む
                if (chunk.length > 0) {
                    mAudioTrack.write(chunk, 0, chunk.length);
                }
                codec.releaseOutputBuffer(outputBufIndex, false);
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Subsequent data will conform to new format.
                format = codec.getOutputFormat();
                mAudioTrack.setPlaybackRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
            }
        }
        // 終了処理
        mAudioTrack.stop();
        extractor.release();
        extractor = null;
        codec.stop();
        codec.release();
        codec = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

}
