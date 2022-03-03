package de.btegermany.terraplusminus.data;

import LZMA.LzmaInputStream;
import io.netty.buffer.ByteBuf;
import net.buildtheearth.terraminusminus.dataset.builtin.AbstractBuiltinDataset;
import net.buildtheearth.terraminusminus.util.RLEByteArray;
import net.daporkchop.lib.common.reference.cache.Cached;

import java.io.IOException;
import java.util.function.Supplier;

public class KoppenClimateData extends AbstractBuiltinDataset {
    protected static final int COLUMNS = 43200;
    protected static final int ROWS = 21600;


    public KoppenClimateData() {
        super(COLUMNS, ROWS);
    }

    private static final Cached<RLEByteArray> CACHE = Cached.global((Supplier<RLEByteArray>) () -> {
        ByteBuf buffered = null;
        RLEByteArray.Builder builder = RLEByteArray.builder();

        try(LzmaInputStream is = new LzmaInputStream(KoppenClimateData.class.getResourceAsStream("/koppen_map.lzma"))){

            byte[] buffer = new byte[4096];
            int readyBytes = 0;
            while(true){
                try {
                    if ((readyBytes = is.read(buffer, 0, 4096)) == -1) break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                for(int i = 0; i < readyBytes; i++){
                    builder.append(buffer[i]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return builder.build();
    });



    private  final  RLEByteArray data = CACHE.get();

    @Override
    protected double get(double xc, double yc) {
        int x = (int) Math.floor(xc);
        int y = (int) Math.floor(yc);

        if(x >= COLUMNS || x < 0 || y >= ROWS || y < 0)
            return 0;

        return  this.data.get(y * COLUMNS + x);
    }

}
