package shiro

import net.liftweb.common.{Box,Full,Empty,Failure}
import net.liftweb.http.{DispatchSnippet,S}
import net.liftweb.util.Helpers.tryo
import org.apache.shiro.SecurityUtils
import org.apache.shiro.subject.Subject
import scala.xml.NodeSeq

import org.apache.shiro.authc.AuthenticationToken

trait SubjectLifeCycle {
  import org.apache.shiro.authc.{IncorrectCredentialsException,
    UnknownAccountException, LockedAccountException, ExcessiveAttemptsException}
  
  protected def logout = SecurityUtils.getSubject.logout
  protected def login[T <: AuthenticationToken](token: T){
    def redirect = S.redirectTo(LoginRedirect.is.openOr("/"))
    val subject = SecurityUtils.getSubject
    if(!subject.isAuthenticated){
      tryo(subject.login(token)) match {
        case Failure(_,Full(err),_) => err match {
          case x: UnknownAccountException => 
            S.error("Unkown user account")
          case x: IncorrectCredentialsException => 
            S.error("Invalid username or password")
          case x: LockedAccountException => 
            S.error("Your account has been locked")
          case x: ExcessiveAttemptsException => 
            S.error("You have exceeded the number of login attempts")
          case _ => 
            S.error("Unexpected login error")
        }
        case _ => redirect
      }
    } else redirect
  }
}

sealed trait Utils {
  protected def serve(xhtml: NodeSeq)(f: (Subject, String) => Boolean): NodeSeq =
    serve("name", xhtml)(f)
  
  protected def serve(attribute: String, xhtml: NodeSeq)(f: (Subject, String) => Boolean): NodeSeq = 
    (for {
      s <- Box.!!(SecurityUtils.getSubject)
      attr <- S.attr(attribute) if f(s,attr)
    } yield xhtml) getOrElse NodeSeq.Empty
}

object Subjects extends DispatchSnippet with Utils {
  def dispatch = {
    case "has_role" => hasRole _
    case "lacks_role" => lacksRole _
    case "has_permission" => hasPermission _
    case "lacks_permission" => lacksPermission _
    case "has_any_roles" => hasAnyRoles _
  }
  
  def hasRole(xhtml: NodeSeq): NodeSeq = serve(xhtml){ 
    (s,r) => s.hasRole(r)
  }
  
  def lacksRole(xhtml: NodeSeq): NodeSeq = serve(xhtml){
    (s,r) => !s.hasRole(r)
  }

  def hasPermission(xhtml: NodeSeq): NodeSeq = serve(xhtml){
    (s,p) => s.isPermitted(p)
  }
  
  def lacksPermission(xhtml: NodeSeq): NodeSeq = serve(xhtml){
    (s,p) => !s.isPermitted(p)
  }
  
  def hasAnyRoles(xhtml: NodeSeq): NodeSeq = {
    val delimiter = ","
    serve("roles", xhtml){ (s,roles) => 
      roles.split(delimiter).map(
        r => s.hasRole(r.trim)).contains(true)
    }
  }
}

import net.liftweb._, util.Helpers._, http.{DispatchSnippet,SHtml}
import org.apache.shiro.authc.UsernamePasswordToken

trait DefaultUsernamePasswordLogin extends DispatchSnippet with SubjectLifeCycle {
  def render = {
    var username = ""
    var password = ""
    "type=text" #> SHtml.text(username, username = _) &
    "type=password" #> SHtml.password(password, password = _) &
    "type=submit" #> SHtml.submit("Login", () => 
      login(new UsernamePasswordToken(username, password)))
  }
}
