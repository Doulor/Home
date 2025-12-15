/**
 * 高级动画工具函数
 */

/**
 * 页面滚动动画
 * @param {number} duration - 动画持续时间（毫秒）
 * @param {string} easing - 缓动函数类型
 */
export const smoothScrollTo = (target, duration = 500, easing = 'easeOutCubic') => {
  const start = window.pageYOffset;
  const startTime = performance.now();

  const scrollTo = (currentTime) => {
    const elapsed = currentTime - startTime;
    const progress = Math.min(elapsed / duration, 1);
    
    const easingFunction = getEasingFunction(easing);
    const factor = easingFunction(progress);
    
    const currentPosition = start + (target - start) * factor;
    window.scrollTo(0, currentPosition);

    if (progress < 1) {
      requestAnimationFrame(scrollTo);
    }
  };

  requestAnimationFrame(scrollTo);
};

/**
 * 获取缓动函数
 * @param {string} type - 缓动类型
 */
const getEasingFunction = (type) => {
  const easings = {
    linear: t => t,
    easeInCubic: t => t * t * t,
    easeOutCubic: t => --t * t * t + 1,
    easeInOutCubic: t => t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1,
    easeInQuart: t => t * t * t * t,
    easeOutQuart: t => 1 - (--t) * t * t * t,
    easeInOutQuart: t => t < 0.5 ? 8 * t * t * t * t : 1 - 8 * (--t) * t * t * t
  };

  return easings[type] || easings.easeOutCubic;
};

/**
 * 元素进入视口动画
 * @param {HTMLElement} element - 目标元素
 * @param {string} className - 动画类名
 */
export const animateOnScroll = (element, className = 'animate__animated animate__fadeInUp') => {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add(...className.split(' '));
        observer.unobserve(entry.target);
      }
    });
  }, {
    threshold: 0.1
  });

  observer.observe(element);
};

/**
 * 颤动效果
 * @param {HTMLElement} element - 目标元素
 * @param {number} intensity - 颤动强度
 * @param {number} duration - 持续时间
 */
export const shakeElement = (element, intensity = 10, duration = 500) => {
  const originalTransform = element.style.transform;
  let startTime = null;

  const shake = (currentTime) => {
    if (!startTime) startTime = currentTime;
    const elapsed = currentTime - startTime;

    if (elapsed < duration) {
      const offsetX = (Math.random() - 0.5) * intensity;
      const offsetY = (Math.random() - 0.5) * intensity;
      element.style.transform = `translate(${offsetX}px, ${offsetY}px)`;
      requestAnimationFrame(shake);
    } else {
      element.style.transform = originalTransform;
    }
  };

  requestAnimationFrame(shake);
};

/**
 * 渐入效果
 * @param {HTMLElement} element - 目标元素
 * @param {number} duration - 持续时间
 */
export const fadeInElement = (element, duration = 500) => {
  element.style.opacity = '0';
  element.style.transition = `opacity ${duration}ms ease-in-out`;

  setTimeout(() => {
    element.style.opacity = '1';
  }, 10);
};

/**
 * 高级光晕效果
 * @param {HTMLElement} element - 目标元素
 * @param {object} options - 光晕选项
 */
export const addGlowEffect = (element, options = {}) => {
  const defaultOptions = {
    color: '#64b5f6',
    size: '10px',
    intensity: 0.5,
    duration: 2000,
    ...options
  };

  // 检查是否已有光晕效果
  const existingGlow = element.querySelector('.glow-overlay');
  if (existingGlow) {
    existingGlow.remove();
  }

  // 创建光晕元素
  const glowElement = document.createElement('div');
  glowElement.className = 'glow-overlay';
  glowElement.style.cssText = `
    position: absolute;
    top: -5px;
    left: -5px;
    right: -5px;
    bottom: -5px;
    border-radius: inherit;
    background: radial-gradient(circle, 
      ${defaultOptions.color} 0%, 
      transparent 70%);
    opacity: 0;
    pointer-events: none;
    z-index: -1;
  `;

  // 添加到元素中
  element.style.position = 'relative';
  element.appendChild(glowElement);

  // 动画
  let start;
  const animate = (timestamp) => {
    if (!start) start = timestamp;
    const progress = timestamp - start;
    const intensity = Math.sin(progress / defaultOptions.duration * Math.PI) * defaultOptions.intensity;

    glowElement.style.opacity = intensity;
    glowElement.style.filter = `blur(${parseInt(defaultOptions.size) * (0.5 + intensity)}px)`;

    if (progress < defaultOptions.duration) {
      requestAnimationFrame(animate);
    } else {
      glowElement.remove();
    }
  };

  requestAnimationFrame(animate);
};