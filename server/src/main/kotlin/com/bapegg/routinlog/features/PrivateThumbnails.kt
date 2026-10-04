package com.bapegg.routinlog.features

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

data class ThumbnailDto(val jpegBase64:String?=null,val version:Long?=null)
/** Bounded 256px private thumbnails for the MVP. Originals/EXIF are never retained or publicly served. */
@Service @Transactional
class PrivateThumbnails(private val jdbc:JdbcTemplate,private val accounts:FeatureService) {
    private fun table(kind:String)=when(kind){"food"->"food_thumbnails";"exercise"->"exercise_thumbnails";else->invalid()}
    private fun resource(kind:String)=when(kind){"food"->"foods";"exercise"->"workout_exercises";else->invalid()}
    private fun check(owner:UUID,kind:String,id:UUID,write:Boolean=false) {
        accounts.account(owner,write)
        if(jdbc.queryForObject("SELECT COUNT(*) FROM ${resource(kind)} WHERE user_id=? AND id=?",Long::class.java,owner,id)!=1L)
            throw FeatureException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","이 항목을 찾을 수 없어요.")
    }
    fun get(owner:UUID,kind:String,id:UUID):ThumbnailDto {
        check(owner,kind,id)
        return jdbc.query("SELECT jpeg,version FROM ${table(kind)} WHERE user_id=? AND resource_id=?",{rs,_->ThumbnailDto(Base64.getEncoder().encodeToString(rs.getBytes(1)),rs.getLong(2))},owner,id).firstOrNull() ?: ThumbnailDto()
    }
    fun put(owner:UUID,kind:String,id:UUID,write:ThumbnailDto):ThumbnailDto {
        check(owner,kind,id,true)
        val old=get(owner,kind,id)
        if(old.version!=write.version)throw FeatureException(HttpStatus.CONFLICT,"VERSION_CONFLICT","사진이 바뀌었어요. 다시 불러와주세요.")
        if(write.jpegBase64==null) {
            jdbc.update("DELETE FROM ${table(kind)} WHERE user_id=? AND resource_id=?",owner,id);return ThumbnailDto()
        }
        if(old.version==null&&jdbc.queryForObject("SELECT COUNT(*) FROM ${table(kind)} WHERE user_id=?",Long::class.java,owner)!!>=200)
            throw FeatureException(HttpStatus.BAD_REQUEST,"PHOTO_LIMIT","사진은 종류별 200개까지 저장할 수 있어요.")
        val jpeg=sanitize(write.jpegBase64)
        val version=(old.version ?: -1)+1
        if(old.version==null)jdbc.update("INSERT INTO ${table(kind)}(user_id,resource_id,version,jpeg) VALUES(?,?,?,?)",owner,id,version,jpeg)
        else jdbc.update("UPDATE ${table(kind)} SET version=?,jpeg=? WHERE user_id=? AND resource_id=?",version,jpeg,owner,id)
        return ThumbnailDto(Base64.getEncoder().encodeToString(jpeg),version)
    }
    internal fun sanitize(encoded:String):ByteArray {
        if(encoded.length !in 1..180000)invalid()
        val bytes=try { Base64.getDecoder().decode(encoded) }catch(_:IllegalArgumentException){invalid()}
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { stream->
            val readers=ImageIO.getImageReaders(stream)
            if(!readers.hasNext())invalid()
            val reader=readers.next()
            try {
                if(!reader.formatName.equals("JPEG",true))invalid()
                reader.input=stream
                val width=reader.getWidth(0);val height=reader.getHeight(0)
                if(width !in 1..1024||height !in 1..1024)invalid()
                val input=reader.read(0)
                val scale=256.0/maxOf(width,height)
                val output=BufferedImage(maxOf(1,(width*minOf(1.0,scale)).toInt()),maxOf(1,(height*minOf(1.0,scale)).toInt()),BufferedImage.TYPE_INT_RGB)
                val graphics=output.createGraphics();try { graphics.drawImage(input,0,0,output.width,output.height,null) }finally { graphics.dispose() }
                return ByteArrayOutputStream().use { ImageIO.write(output,"jpeg",it);it.toByteArray() }
            }catch(e:FeatureException){throw e}catch(_:Exception){invalid()}finally { reader.dispose() }
        }
    }
    private fun invalid():Nothing=throw FeatureException(HttpStatus.BAD_REQUEST,"INVALID_IMAGE","작은 JPEG 사진으로 다시 선택해주세요.")
}
