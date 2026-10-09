<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Create account" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<div class="auth">
  <section class="auth-side">
    <p class="display">Origin</p>
  </section>
  <section class="auth-form">
    <form class="form" method="post" action="${ctx}/register" novalidate>
      <h1 class="h1">Create account</h1>
      <input type="hidden" name="_csrf" value="${csrf}">
      <div class="field ${not empty errors.fullName ? 'bad' : ''}">
        <label for="fullName">Full name</label>
        <input id="fullName" name="fullName" autocomplete="name" required value="<c:out value='${form.fullName}'/>">
        <c:if test="${not empty errors.fullName}"><p class="err"><c:out value="${errors.fullName}"/></p></c:if>
      </div>
      <div class="field ${not empty errors.email ? 'bad' : ''}">
        <label for="email">College email</label>
        <input id="email" name="email" type="email" autocomplete="email" required value="<c:out value='${form.email}'/>">
        <c:if test="${not empty errors.email}"><p class="err"><c:out value="${errors.email}"/></p></c:if>
      </div>
      <div class="field ${not empty errors.password ? 'bad' : ''}">
        <label for="password">Password</label>
        <p class="hint">At least 8 characters, with letters and numbers.</p>
        <input id="password" name="password" type="password" autocomplete="new-password" required>
        <c:if test="${not empty errors.password}"><p class="err"><c:out value="${errors.password}"/></p></c:if>
      </div>
      <div class="field ${not empty errors.groupCode ? 'bad' : ''}">
        <label for="groupCode">Project group</label>
        <p class="hint">Your supervisor gives each group a code like G07. You can leave this empty for now.</p>
        <input id="groupCode" name="groupCode" maxlength="3" placeholder="G07" value="<c:out value='${form.groupCode}'/>">
        <c:if test="${not empty errors.groupCode}"><p class="err"><c:out value="${errors.groupCode}"/></p></c:if>
      </div>
      <div><button class="btn btn-lg" type="submit">Create account</button></div>
      <p>Already registered? <a class="link" href="${ctx}/login">Sign in</a>.</p>
    </form>
  </section>
</div>
<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>
