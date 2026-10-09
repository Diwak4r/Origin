<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Origin" scope="request"/>
<c:set var="bodyClass" value="home" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<section class="welcome">
  <div class="wrap welcome-grid">
    <div class="welcome-lead">
      <h1 class="welcome-head">Check your idea before you build it.</h1>
    </div>
    <div class="welcome-act">
      <a class="btn btn-lg welcome-go" href="${ctx}/login">Sign in</a>
      <p class="welcome-alt">New here? <a class="link" href="${ctx}/register">Create an account</a>.</p>
    </div>
  </div>
</section>

<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>
