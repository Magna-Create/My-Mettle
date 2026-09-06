package dev.kian.lab2b.vlm

import org.json.JSONArray
import org.json.JSONObject

object OcrJsonCodec {
    fun image(i: SelectedImageInfo?): Any = if (i==null) JSONObject.NULL else (TestReport.image(i) as JSONObject).put("source_private_path",i.sourcePrivatePath)
    private fun strings(a: JSONArray)=(0 until a.length()).map { a.getString(it) }
    private fun readBox(a: JSONArray?)=a?.let { OcrBox(it.getInt(0),it.getInt(1),it.getInt(2),it.getInt(3)) }
    private fun corners(a: JSONArray)=(0 until a.length()).map { a.getJSONArray(it).let { p -> OcrPoint(p.getInt(0),p.getInt(1)) } }
    fun readOcr(e: JSONObject): OcrEvidence {
        val blocks=e.getJSONArray("blocks")
        return OcrEvidence(e.getString("full_text"),(0 until blocks.length()).map { n -> val b=blocks.getJSONObject(n); val lines=b.getJSONArray("lines")
            OcrBlock(b.getString("text"),readBox(b.optJSONArray("box")),corners(b.getJSONArray("corners")),if(b.isNull("language")) null else b.getString("language"),
                (0 until lines.length()).map { j -> val l=lines.getJSONObject(j); OcrLine(l.getString("text"),readBox(l.optJSONArray("box")),corners(l.getJSONArray("corners")),if(l.isNull("language")) null else l.getString("language")) })
        },e.getLong("processing_ms"),e.getString("source_sha256"),e.getInt("width"),e.getInt("height"),e.getString("recognizer"))
    }
    fun readImage(i: JSONObject?): SelectedImageInfo? = i?.let { SelectedImageInfo(
        sourceName=it.getString("source_name"),sourcePrivatePath=it.getString("source_private_path"),sourceBytes=it.getLong("source_bytes"),
        sourceWidth=it.getInt("source_width"),sourceHeight=it.getInt("source_height"),sourceSha256=it.getString("source_sha256"),orientation=it.getInt("exif_orientation"),normalisation=it.getString("normalisation"),
        normalisedPath=it.getString("normalised_path"),normalisedSha256=it.getString("normalised_sha256"),normalisedWidth=it.getInt("normalised_width"),normalisedHeight=it.getInt("normalised_height"),
        preparedPath=it.getString("prepared_path"),preparedSha256=it.getString("prepared_sha256"),preparedWidth=it.getInt("prepared_width"),preparedHeight=it.getInt("prepared_height"),preparedBytes=it.getLong("prepared_bytes")) }
}
