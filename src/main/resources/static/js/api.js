/**
 * Unified API wrapper.
 * All endpoints return { code, message, data }; code 0 means success.
 */
const API = {
    async request(url, options) {
        let resp;
        try {
            resp = await fetch(url, options);
        } catch (e) {
            throw new Error("网络请求失败，请确认服务已启动");
        }
        let body;
        try {
            body = await resp.json();
        } catch (e) {
            throw new Error("服务响应异常 (HTTP " + resp.status + ")");
        }
        if (!resp.ok || body.code !== 0) {
            throw new Error(body.message || "请求失败 (HTTP " + resp.status + ")");
        }
        return body.data;
    },

    get(url) {
        return this.request(url);
    },

    post(url, data) {
        return this.request(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        });
    },

    upload(url, formData) {
        return this.request(url, {
            method: "POST",
            body: formData
        });
    }
};
