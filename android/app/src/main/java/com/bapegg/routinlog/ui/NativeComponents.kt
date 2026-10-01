package com.bapegg.routinlog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bapegg.routinlog.R
import com.bapegg.routinlog.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@Composable
fun UiCard(dark: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape=RoundedCornerShape(20.dp), color=if(dark) Charcoal else Color.White,
        contentColor=if(dark) Color.White else Ink, border=if(dark) null else BorderStroke(1.dp,Color(0xFFDCE3EB))) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun BodyText(text:String) { Text(text, style=MaterialTheme.typography.bodyLarge) }
@Composable fun MutedText(text:String) { Text(text,style=MaterialTheme.typography.bodyMedium,color=if(LocalContentColor.current==Color.White)Color(0xFFC4CFDC)else Muted) }
@Composable fun SectionTitle(title:String,action:String?=null,onAction:()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(top=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f).semantics { heading() },style=MaterialTheme.typography.titleMedium)
        if(action!=null) TextButton(onClick=onAction){Text(action,color=Muted,fontSize=13.sp)}
    }
}
@Composable fun HeroNumber(value:String,unit:String="") {
    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(value,style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold)
        if(unit.isNotBlank()) Text(unit,Modifier.padding(bottom=6.dp),fontSize=13.sp)
    }
}
@Composable fun UiButton(label:String,onClick:()->Unit,primary:Boolean=true,enabled:Boolean=true,modifier:Modifier=Modifier) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=50.dp),
        shape=RoundedCornerShape(11.dp),border=if(primary)null else BorderStroke(1.dp,Border),
        contentPadding=PaddingValues(horizontal=14.dp,vertical=13.dp),
        colors=ButtonDefaults.buttonColors(containerColor=if(primary)DeepBlue else Color.White,
            contentColor=if(primary)Color.White else Ink, disabledContainerColor=Color(0xFFD9E0E8),disabledContentColor=Muted)) {
        Text(label,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,fontSize=15.sp)
    }
}
@Composable fun UiIcon(name:String,modifier:Modifier=Modifier,tint:Color=Muted) {
    Icon(painterResource(NativeIcons[name]?:R.drawable.ic_circle),null,modifier.size(23.dp),tint=tint)
}
@Composable fun UiRow(title:String,subtitle:String="",value:String="",icon:String?=null,selected:Boolean=false,onClick:(()->Unit)?=null) {
    val click=if(onClick!=null)Modifier.clickable(role=Role.Button,onClick=onClick) else Modifier
    Row(Modifier.fillMaxWidth().then(click).heightIn(min=62.dp).padding(vertical=10.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        if(icon!=null) Surface(shape=RoundedCornerShape(12.dp),color=Silver,border=BorderStroke(1.dp,Color(0xFFDCE3EB))) {
            Box(Modifier.size(46.dp),contentAlignment=Alignment.Center){UiIcon(icon)}
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            if(subtitle.isNotBlank())Text(subtitle,color=Muted,style=MaterialTheme.typography.bodySmall)
        }
        if(value.isNotBlank())Text(value,fontSize=13.sp,color=if(selected)Color(0xFF4D663E)else Muted)
        if(selected) UiIcon("Check",tint=Color(0xFF54703E)) else if(onClick!=null)UiIcon("ChevronRight",Modifier.size(17.dp))
    }
}
@Composable fun Choice(label:String,detail:String="",selected:Boolean,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().semantics { this.selected=selected },
        shape=RoundedCornerShape(14.dp),color=if(selected) Color(0xFFEDF5E6) else Color.White,
        border=BorderStroke(1.dp,if(selected)Color(0xFFB6CE9E)else Border)) {
        Row(Modifier.padding(15.dp).heightIn(min=32.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=RoundedCornerShape(8.dp),color=if(selected)Celery else Silver,border=BorderStroke(1.dp,Border)) {
                Box(Modifier.size(27.dp),contentAlignment=Alignment.Center){if(selected)UiIcon("Check",Modifier.size(18.dp),Ink)}
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(label,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
                if(detail.isNotBlank())Text(detail,color=Muted,fontSize=13.sp,lineHeight=20.sp)
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun Chips(options:List<String>,selected:String,onSelect:(String)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        options.forEach { value ->
            FilterChip(selected=value==selected,onClick={onSelect(value)},label={Text(value,fontSize=13.sp)},
                modifier=Modifier.heightIn(min=48.dp),shape=RoundedCornerShape(12.dp),
                colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Celery,selectedLabelColor=Ink),
                border=FilterChipDefaults.filterChipBorder(enabled=true,selected=value==selected,borderColor=Border,selectedBorderColor=Celery))
        }
    }
}
@Composable fun Input(label:String,value:String,onValueChange:(String)->Unit,suffix:String="",numeric:Boolean=false,multiline:Boolean=false,enabled:Boolean=true) {
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(label,fontSize=13.sp,fontWeight=FontWeight.Medium)
        OutlinedTextField(value=value,onValueChange=onValueChange,enabled=enabled,modifier=Modifier.fillMaxWidth(),
            singleLine=!multiline,minLines=if(multiline)3 else 1,shape=RoundedCornerShape(12.dp),
            suffix=if(suffix.isBlank())null else ({Text(suffix)}),
            keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text),
            colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color.White,focusedContainerColor=Color.White,
                unfocusedBorderColor=Border,focusedBorderColor=DeepBlue),textStyle=MaterialTheme.typography.bodyLarge)
    }
}
@Composable fun Stat(label:String,value:String,unit:String="",modifier:Modifier=Modifier) {
    UiCard(modifier=modifier){MutedText(label);HeroNumber(value,unit)}
}
@Composable fun ProgressLine(fraction:Float,color:Color=Celery) {
    LinearProgressIndicator(progress={fraction.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(6.dp),
        color=color,trackColor=if(LocalContentColor.current==Color.White)Color(0xFF485462)else Color(0xFFDEE4EB),strokeCap=StrokeCap.Round,gapSize=0.dp,drawStopIndicator={})
}
@Composable fun KeyValue(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top) {
        Text(label,Modifier.weight(1f),fontSize=13.sp,color=Muted,lineHeight=20.sp)
        Text(value,Modifier.weight(1f),fontSize=13.sp,textAlign=TextAlign.End,lineHeight=20.sp)
    }
}
@Composable fun Badge(text:String,warning:Boolean=false) {
    Surface(color=if(warning)Color(0xFFFFF0DE)else Color(0xFFEAF3E0),shape=RoundedCornerShape(8.dp)) {
        Text(text,Modifier.padding(horizontal=9.dp,vertical=6.dp),fontSize=12.sp,fontWeight=FontWeight.SemiBold,
            color=if(warning)Color(0xFF905C27)else Color(0xFF536C40))
    }
}
@Composable fun DividerLine(){HorizontalDivider(color=Color(0xFFDCE3EB))}
@Composable fun WeekStrip(selected:Int=2,onSelect:(Int)->Unit={}) {
    val monday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        listOf("월","화","수","목","금","토","일").forEachIndexed { i,label ->
            Surface(onClick={onSelect(i)},modifier=Modifier.weight(1f).semantics { this.selected=selected==i },
                shape=RoundedCornerShape(14.dp),color=if(i==selected)Charcoal else Color(0xFFF8FAFD),
                contentColor=if(i==selected)Color.White else Ink,border=BorderStroke(1.dp,if(i==selected)Charcoal else Border)) {
                Column(Modifier.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(label,fontSize=11.sp);Text(monday.plusDays(i.toLong()).dayOfMonth.toString(),fontWeight=FontWeight.SemiBold)
                    Text(if(i<selected)"✓" else if(i==6)"휴식" else "·",fontSize=10.sp,color=if(i==selected)Celery else Color(0xFF597243))
                }
            }
        }
    }
}
@Composable fun BarChart(values:List<Float>,labels:List<String>,animate:Boolean=true) {
    val maximum=(values.maxOrNull()?:1f).coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth().height(145.dp).semantics { contentDescription=labels.zip(values).joinToString { "${it.first} ${it.second.toInt()}" } },
        horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom) {
        values.forEachIndexed { i,value ->
            val height=remember(values){Animatable(if(animate)0f else value/maximum)}
            LaunchedEffect(values,animate){if(animate){delay(i*230L);height.animateTo(value/maximum,tween(230))}}
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.height((104*height.value.coerceIn(0f,1f)+4).dp).fillMaxWidth().background(if(i==2)Celery else Color(0xFFABBACB),RoundedCornerShape(topStart=5.dp,topEnd=5.dp)))
                Text(labels.getOrElse(i){""},fontSize=12.sp,color=Muted)
            }
        }
    }
}
@Composable fun LineChart(values:List<Float>,modifier:Modifier=Modifier) {
    Canvas(modifier.fillMaxWidth().height(126.dp).semantics { contentDescription="변화 추이: ${values.joinToString()}" }) {
        if(values.size<2)return@Canvas
        val low=(values.minOrNull()?:0f)-.3f;val high=(values.maxOrNull()?:1f)+.3f
        repeat(3){i->val y=size.height*i/2;drawLine(Color(0xFFDCE3EB),Offset(0f,y),Offset(size.width,y),1.dp.toPx())}
        val points=values.mapIndexed{i,v->Offset(i*size.width/(values.size-1),size.height*(1-(v-low)/(high-low)))}
        val line=Path().apply{moveTo(points[0].x,points[0].y);points.drop(1).forEach { lineTo(it.x,it.y) }}
        drawPath(line,DeepBlue,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        points.forEach{drawCircle(DeepBlue,3.dp.toPx(),it)}
    }
}
@Composable fun Stepper(label:String,value:String,unit:String,onChange:(String)->Unit,step:Double=1.0) {
    fun adjust(by:Double){val n=((value.toDoubleOrNull()?:0.0)+by).coerceAtLeast(0.0);onChange(if(n%1.0==0.0)n.toInt().toString()else "%.1f".format(java.util.Locale.US,n))}
    UiCard {
        MutedText(label)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick={adjust(-step)},modifier=Modifier.size(48.dp).semantics{contentDescription="$label 줄이기"},shape=RoundedCornerShape(12.dp),colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=Silver)) {UiIcon("Minus")}
            BasicTextField(value,onValueChange=onChange,modifier=Modifier.weight(1f).semantics { contentDescription=label },singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                textStyle=MaterialTheme.typography.displayMedium.copy(textAlign=TextAlign.Center,fontWeight=FontWeight.Bold,color=Ink))
            Text(unit,fontSize=13.sp,color=Muted)
            FilledTonalIconButton(onClick={adjust(step)},modifier=Modifier.size(48.dp).semantics{contentDescription="$label 늘리기"},shape=RoundedCornerShape(12.dp),colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=Color(0xFFE6EDFF))) {UiIcon("Plus",tint=DeepBlue)}
        }
    }
}
