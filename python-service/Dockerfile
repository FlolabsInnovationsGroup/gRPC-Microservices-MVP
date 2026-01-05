FROM python:3.11-slim

WORKDIR /app

COPY python-service/requirements.txt .
RUN pip install -r requirements.txt

COPY python-service/app/ app/
COPY proto/ proto/

RUN python -m grpc_tools.protoc \
    -I proto \
    --python_out=app \
    --grpc_python_out=app \
    proto/echo.proto

EXPOSE 50051

CMD ["python", "app/server.py"]
