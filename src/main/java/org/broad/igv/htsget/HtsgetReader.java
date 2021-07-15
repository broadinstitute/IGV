package org.broad.igv.htsget;

import com.google.gson.*;
import org.broad.igv.util.HttpUtils;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HtsgetReader {

    String url;
    String format;

    /**
     * Factor method to return an htsget reader for the URL.   In the case of an unexpected response, return "null",
     * this indicates that the URL is not an htsget source.
     *
     * @param url
     * @return
     * @throws IOException
     */
    public static HtsgetReader getReader(final String url) throws IOException {

        String ticket = HttpUtils.getInstance().getContentsAsJSON(new URL(url + "?class=header"));
        JsonParser parser = new JsonParser();
        JsonObject json = null;
        try {
            json = parser.parse(ticket).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            return null;  // Not an htsget server
        }

        if (!json.has("htsget")) {
            // TODO -- throw exception?
            return null;
        }

        JsonObject container = json.get("htsget").getAsJsonObject();
        String format = container.get("format").getAsString();
        switch (format) {
            case "VCF":
                return new HtsgetReader(url, format);
            default:
                throw new RuntimeException("Unsupported htsget format: " + format);
        }
    }


    HtsgetReader(String url, String format) {
        this.url = url;
        this.format = format.toUpperCase();
        if (!(this.format.equals("VCF"))) {
            throw new RuntimeException("Format: " + format + " is not supported");
        }
    }

    public byte [] readHeader() throws IOException {
        String url = this.url + "?class=header"; // + "&format=" + this.format;
        String ticketString = HttpUtils.getInstance().getContentsAsJSON(new URL(url));
        JsonParser parser = new JsonParser();
        JsonObject ticket = parser.parse(ticketString).getAsJsonObject();
        return loadURLs(ticket);
    }

    public byte[] readData(String chr, int start, int end) throws IOException {
        String url = this.url + "?format=" + this.format + "&referenceName=" + chr + "&start=" + start + "&end=" + end;
        String ticketString = HttpUtils.getInstance().getContentsAsJSON(new URL(url));
        JsonParser parser = new JsonParser();
        JsonObject ticket = parser.parse(ticketString).getAsJsonObject();
        return loadURLs(ticket);
    }

    public String getFormat() {
        return format;
    }

    private byte[] loadURLs(JsonObject ticket) throws IOException {

        JsonObject container = ticket.getAsJsonObject("htsget");
        JsonArray urls = container.get("urls").getAsJsonArray();
        Iterator<JsonElement> iter = urls.iterator();
        byte[] bytes = null;
        while (iter.hasNext()) {

            JsonObject next = iter.next().getAsJsonObject();
            String urlString = next.get("url").getAsString();
            if(urlString.startsWith("data:")) {
                throw new RuntimeException("Data URLs are not currently supported");
            }

            URL url = new URL(urlString);
            Map<String, String> headers = new HashMap<>();
            JsonObject headerObj = next.getAsJsonObject("headers");
            if(headerObj != null) {
                for (String key : headerObj.keySet()) {
                    headers.put(key, headerObj.get(key).getAsString());
                }
            }

            byte[] b = HttpUtils.getInstance().getContentsAsBytes(url, headers);
            if (bytes == null) {
                bytes = b;
            } else {
                byte[] newBytes = new byte[bytes.length + b.length];
                System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                System.arraycopy(b, 0, newBytes, bytes.length, b.length);
                bytes = newBytes;
            }
        }
        return bytes;
    }
}

//    async readData(chr, start, end) {
//        const url = `${getUrl(this.config)}?format=${this.format}&referenceName=${chr}&start=${start}&end=${end}`;
//        const ticket = await igvxhr.loadJson(url, buildOptions(this.config));
//        return this.loadUrls(ticket.htsget.urls);
//    }
//
//    async loadUrls(urls) {
//
//        const promiseArray = [];
//        for (let urlData of urls) {
//
//            if (urlData.url.startsWith('data:')) {
//                // this is a data-uri
//                promiseArray.push(Promise.resolve(dataUriToBytes(urlData.url)));
//
//            } else {
//
//                const options = buildOptions(this.config || {});
//
//                if (urlData.headers) {
//                    options.headers = Object.assign(options.headers || {}, urlData.headers);
//                }
//
//                promiseArray.push(igvxhr.loadArrayBuffer(urlData.url, options));
//            }
//        }
//        const arrayBuffers = await Promise.all(promiseArray);
//        return concatArrays(arrayBuffers);
//    }
//
//
//
//
//    /**
//     * Concatenate a list of array buffers, returning an UInt8Array
//     * @param arrayBuffers
//     */
//    function concatArrays(arrayBuffers) {
//
//        let len = 0;
//        for (let a of arrayBuffers) {
//            len += a.byteLength;
//        }
//
//        let offset = 0;
//    const newArray = new Uint8Array(len);
//        for (let buf of arrayBuffers) {
//        const a = new Uint8Array(buf);
//            newArray.set(a, offset);
//            offset += a.length;
//        }
//
//        return newArray;
//    }
//
//    function dataUriToBytes(dataUri) {
//
//    const split = dataUri.split(',');
//    const info = split[0].split(':')[1];
//        let dataString = split[1];
//
//        if (info.indexOf('base64') >= 0) {
//            dataString = atob(dataString);
//        } else {
//            dataString = decodeURI(dataString);
//        }
//
//    const bytes = new Uint8Array(dataString.length);
//        for (var i = 0; i < dataString.length; i++) {
//            bytes[i] = dataString.charCodeAt(i);
//        }
//
//        return bytes;
//    }
//
//
//export default HtsgetReader;
