/*
* Copyright 2007-2008 WorldWide Conferencing, LLC
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions
* and limitations under the License.
*/
  
package net.liftweb.widgets.calendar;

import scala.xml._
import java.util.{Calendar, Locale}
import java.util.Calendar._
import java.text.SimpleDateFormat
import net.liftweb.util.Helpers._
import net.liftweb.util.{Can, Full, Empty}
import net.liftweb.http.js._
import net.liftweb.http.SHtml._
import JsCmds._
import JE._

object CalendarMonthView {

  /**
   * Call this function typically in boot
   */
  def init() {
    import net.liftweb.http.ResourceServer
    ResourceServer.allow({
      case "calendars" :: tail => true
    })
  }

  def apply(when: Calendar,
            calendars: Seq[CalendarItem], 
            itemClick: Can[AnonFunc], 
            dayClick: Can[AnonFunc], 
            weekClick: Can[AnonFunc]) = new CalendarMonthView(when).render(calendars, itemClick, dayClick, weekClick)
            
  def apply(when: Calendar,
            meta: CalendarMeta,
            calendars: Seq[CalendarItem], 
            itemClick: Can[AnonFunc], 
            dayClick: Can[AnonFunc], 
            weekClick: Can[AnonFunc]) = new CalendarMonthView(when, meta) render(calendars, itemClick, dayClick, weekClick)


}

/**
 * CalendarMonthView renders a month view representation of a collection of CalendarItem
 * <br> 
 * Usage example - assume CalendarView is a typical LiftWeb snippet
 * <pre>
 * class CalendarView {
 * 
 *  def render(html: Group) : NodeSeq = {
 *    val c = Calendar getInstance;
 *    c.set(MONTH, 4)
 *    bind("cal", html,
 *         "widget" --> CalendarMonthView(c, makeCals, itemClick, dayClick, weekClick)
 *    )
 *  }
 *  
 *  import JE._
 *  import JsCmds._
 *  
 *  def itemClick = Full(AnonFunc("elem, param", JsRaw("alert(param + ' - ' + elem.nodeName)")))
 *  def dayClick = Full(AnonFunc("elem, param", JsRaw("alert(param + ' - ' + elem.nodeName)")))
 *  def weekClick = Full(AnonFunc("elem, param", JsRaw("alert(param + ' - ' + elem.nodeName)")))
 * 
 *  
 *  private def makeCals = {
 *    val c1 = Calendar getInstance
 *    val c2 = Calendar getInstance;
 *    val c3 = Calendar getInstance;
 * 
 *    c2.set(DAY_OF_MONTH, 3)
 *    c3.set(DAY_OF_MONTH, 1)
 *    c3.set(MONTH, 4)
 * 
 *    val item1 = CalendarItem(...)
 *    val item2 = CalendarItem(...)
 *    val item3 = CalendarItem(...)
 *    
 *    item1 :: item2 :: item3 ::  Nil
 *  }
 * }
 * 
 * </pre>
 *
 * @param when - the Calendar object describing the month that needs tobe rendered
 *
 */
class CalendarMonthView(val when: Calendar, val meta: CalendarMeta) {
  private lazy val weekDaysFormatter = new SimpleDateFormat("EEEE", meta.locale)
  private lazy val timeFormatter = new SimpleDateFormat("hh:mm", meta.locale)
  private lazy val dateFormatter = new SimpleDateFormat("MM/dd/yyyy", meta.locale)
  
  def this(when: Calendar) = this(when, CalendarMeta(MONDAY, Locale getDefault))
  
  /**
   * Returns the markup for rendering the calendar month view
   *
   * @param calendars - the calendar items than need to be rendered
   * @param itemClick - Ajax function to be called when a calendar item was clicked. 
   *                    It takes two parameters: elem the node that was clicked and 
   *                    param the identifier if this CalendarItem
   * @param dayClick - Ajax function to be called when a day number(cell header) item was clicked
   *                   It takes two parameters: elem the node that was clicked and 
   *                   param the date of the clicked day in MM/dd/yyyy format
   * @param weekClick - Ajax function to be called when a day number(cell header) item was clicked
   *                   It takes two parameters: elem the node that was clicked and 
   *                   the week number
   * @return NodeSeq - the markup to be rendered  
   */
  def render(calendars: Seq[CalendarItem], 
             itemClick: Can[AnonFunc], 
             dayClick: Can[AnonFunc],
             weekClick: Can[AnonFunc]): NodeSeq = {
     
    def makeCells(calendar: Calendar): NodeSeq = {
      
      def predicate (current: Calendar, c: CalendarItem) = {
        // Adjust the precision
        current.set(MILLISECOND, c.start.get(MILLISECOND))
        current.set(SECOND, c.start.get(SECOND))
        current.set(MINUTE, c.start.get(MINUTE))
        current.set(HOUR_OF_DAY, c.start.get(HOUR_OF_DAY))
        
        c end match {
          case Empty => current.get(DAY_OF_MONTH) >= c.start.get(DAY_OF_MONTH) && current.get(MONTH) >= c.start.get(MONTH)
          case Full(end) => {
            val crt = current getTimeInMillis;
            (crt >= c.start.getTimeInMillis) && (crt <= end.getTimeInMillis)
          }
        }
      }
      val thisMonth = when get(MONTH)
      val cal = calendar.clone().asInstanceOf[Calendar] 
      val today = Calendar getInstance (meta locale)
      (0 to 5) map (row => <tr><td wk={cal get(WEEK_OF_YEAR) toString} 
                                   class={meta.cellWeek} 
                                   onclick={JsFunc("weekClick", JsRaw("this"), Jq(JsRaw("this")) >> JqGetAttr("wk")).toJsCmd}>
        {cal get(WEEK_OF_YEAR)}</td>{(0 to 6) map (col => 
        try{
         <td>{
            val day = cal.get(DAY_OF_MONTH)
            val month = cal.get(MONTH)
            val isToday = today.get(DAY_OF_MONTH) == cal.get(DAY_OF_MONTH) && (month == today.get(MONTH))
            val div = <div>{
              calendars filter (c => predicate(cal, c)) map (c => {
                val r = <div><a href="#">{
                   <span>{timeFormatter format(c.start.getTime)} {c.subject openOr "..."}</span> 
                }</a></div> % ("class" -> meta.calendarItem) % 
                  ("rec_id" -> c.id) % 
                  ("onclick" -> JsFunc("itemClick", JsRaw("this"), Jq(JsRaw("this")) >> JqGetAttr("rec_id")).toJsCmd)
                  
                c.description map (desc => r % (("title" -> desc))) openOr r
              }
              )
            }</div>
            val (head, cell) = isToday match {
              case true => (meta.cellHeadToday, meta.cellBodyToday)
              case _ => (month != thisMonth) match {
                case true => (meta.cellHeadOtherMonth, meta.cellBodyOtherMonth)
                case _ => (meta.cellHead, meta.cellBody)
              }
            }
            Group(<div>{day}</div> % 
              ("class" -> head) :: 
              div % ("class" -> cell) :: Nil)
          }</td> % ("date" -> (dateFormatter format(cal getTime))) %
            ("onclick" -> JsFunc("dayClick", JsRaw("this"), (Jq(JsRaw("this")) >> JqGetAttr("date"))).toJsCmd)
        } finally {
          cal add(DAY_OF_MONTH, 1)
        }
        ) 
      }</tr>)
    }
    
    def makeHead(headCal: Calendar) = <tr><td></td>{
      (0 to 6) map(x => <td width="14%">{
        try{
          weekDaysFormatter format(headCal getTime)
        } finally {
          headCal add(DAY_OF_MONTH, 1)
        }
      }</td>)
    }</tr>
    
    val cal = when.clone().asInstanceOf[Calendar]
    cal set(DAY_OF_MONTH, 1)
    val delta = cal.get(DAY_OF_WEEK) - meta.firstDayOfWeek
    cal add(DAY_OF_MONTH, if (delta < 0) -delta-7 else -delta)
    
    val headCal = cal.clone().asInstanceOf[Calendar]
    
    val init = JsRaw("""
      jQuery(function($){
        jQuery('.calendarItem').click(function(e){
          e.stopPropagation();
        });
        jQuery('.calendarItem').tooltip({ 
          track: true, 
          delay: 0, 
          showURL: false
        });
      })
      """) & 
      JsCrVar("itemClick", itemClick openOr JsRaw("function(param){}")) &
      JsCrVar("dayClick", dayClick openOr JsRaw("function(param){}")) & 
      JsCrVar("weekClick", weekClick openOr JsRaw("function(param){}"))
    
      <head>
        <link rel="stylesheet" href="/classpath/calendars/monthview/style.css" type="text/css"/>
        <script type="text/javascript" src="/classpath/calendars/monthview/jquery.dimensions.js"></script>
        <script type="text/javascript" src="/classpath/calendars/monthview/jquery.bgiframe.js"></script>
        <script type="text/javascript" src="/classpath/calendars/monthview/jquery.tooltip.js"></script>
        <script type="text/javascript" charset="utf-8">{Unparsed(init toJsCmd)}</script>
      </head>
      <div class={meta.monthView}>{
        <table width="100%" cellspacing="1" cellpadding="0" style="table-layout: fixed;" class={meta.topHead}>
          {makeHead(headCal)}
          {makeCells(cal)}
        </table> 
      }</div>
  }
}

