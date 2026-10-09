<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Sign in" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<div class="auth">
  <section class="auth-side">
    <p class="auth-mark">Origin</p>
    <div class="auth-side-body">
      <h2 class="auth-message">A clear start for your project.</h2>
      <p class="auth-note">Sign in to review ideas, compare past projects, and manage your group proposal.</p>
    </div>
    <p class="auth-college">Himalayan Whitehouse International College</p>
  </section>
  <section class="auth-form">
    <form class="form" method="post" action="${ctx}/login" novalidate>
      <div class="auth-heading"><h1 class="h1">Welcome back</h1><p>Use your college account to continue.</p></div>
      <input type="hidden" name="_csrf" value="${csrf}">
      <c:if test="${not empty next}"><input type="hidden" name="next" value="<c:out value='${next}'/>"></c:if>
      <c:if test="${not empty error}"><p class="form-error" role="alert"><c:out value="${error}"/></p></c:if>
      <div class="field">
        <label for="email">Email</label>
        <input id="email" name="email" type="email" autocomplete="username" required value="<c:out value='${email}'/>">
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input id="password" name="password" type="password" autocomplete="current-password" required>
      </div>
      <div class="auth-submit"><button class="btn btn-lg" type="submit">Sign in</button></div>
      <p class="auth-alt">No account yet? <a class="link" href="${ctx}/register">Create one</a>.</p>
    </form>
  </section>
</div>
<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>