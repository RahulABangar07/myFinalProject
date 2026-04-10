@Component
public class CustomHeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String host = httpRequest.getHeader("Host");

        HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
            @Override
            public String getHeader(String name) {
                if ("X-Custom-Header".equalsIgnoreCase(name)) {
                    if ("example.com".equalsIgnoreCase(host)) {
                        return "VALUE_1";
                    } else if ("another.com".equalsIgnoreCase(host)) {
                        return "VALUE_2";
                    }
                }
                return super.getHeader(name);
            }
        };

        chain.doFilter(wrappedRequest, response);
    }
}
