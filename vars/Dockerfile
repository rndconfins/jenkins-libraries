FROM nginx:1.24.0-alpine
WORKDIR /usr/share/nginx/html
RUN rm -rf ./*
COPY default.conf /etc/nginx/conf.d/default.conf
COPY /dist /usr/share/nginx/html
ENTRYPOINT ["nginx", "-g", "daemon off;"]
